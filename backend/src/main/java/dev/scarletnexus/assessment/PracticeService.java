package dev.scarletnexus.assessment;

import static dev.scarletnexus.common.DomainFailure.require;

import dev.scarletnexus.assessment.PracticeController.*;
import dev.scarletnexus.assessment.PracticeModels.*;
import dev.scarletnexus.common.*;
import dev.scarletnexus.identity.CurrentUser;
import dev.scarletnexus.learning.*;
import dev.scarletnexus.learning.LearningModels.Page;
import dev.scarletnexus.skill.CatalogMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PracticeService {
  private final PracticeMapper db;
  private final LearningService learning;
  private final CatalogMapper catalog;
  private final AuditLog audit;

  public PracticeService(
      PracticeMapper db, LearningService learning, CatalogMapper catalog, AuditLog audit) {
    this.db = db;
    this.learning = learning;
    this.catalog = catalog;
    this.audit = audit;
  }

  private UUID owner() {
    return CurrentUser.id();
  }

  private void lock() {
    if (catalog.lockOwner(owner()) == null) throw DomainFailure.missing();
  }

  private <T> T found(T v) {
    if (v == null) throw DomainFailure.missing();
    return v;
  }

  private void page(int p, int s) {
    require(p >= 0 && p <= 100000 && s > 0 && s <= 100, "INVALID_PAGE", "分页参数无效");
  }

  private void key(String key) {
    require(key != null && key.matches("[A-Za-z0-9_-]{1,100}"), "INVALID_KEY", "操作键无效");
  }

  public static String hash(String input) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Hash unavailable");
    }
  }

  private void same(boolean value) {
    if (!value) throw new DomainFailure(HttpStatus.CONFLICT, "PRACTICE_KEY_CONFLICT", "操作键已用于其他请求");
  }

  private Secret secret(UUID id) {
    return found(db.secret(owner(), id));
  }

  public Page<Question> questions(UUID task, int p, int s) {
    var t = learning.task(task);
    page(p, s);
    return new Page<>(
        db.questions(owner(), t.nodeId(), s, p * s), db.questionCount(owner(), t.nodeId()), p, s);
  }

  public Question createQuestion(UUID task, QuestionWrite v) {
    lock();
    var t = learning.task(task);
    require(
        !v.questionType().equals("EXACT_TEXT") || !v.answerKey().isBlank(),
        "ANSWER_REQUIRED",
        "精确文本题需设置比对答案");
    UUID id = UUID.randomUUID();
    db.questionInsert(
        owner(),
        id,
        t.nodeId(),
        v.title().strip(),
        v.prompt(),
        v.questionType(),
        v.answerKey(),
        v.hintText());
    audit.record(
        "question",
        id,
        "CREATE",
        null,
        Map.of("nodeId", t.nodeId(), "questionType", v.questionType()));
    return secret(id).view();
  }

  public Question questionState(UUID id, String status) {
    lock();
    var before = secret(id).view();
    db.questionState(owner(), id, status);
    var after = secret(id).view();
    audit.record("question", id, "STATE", before, after);
    return after;
  }

  public Attempt attempt(UUID id) {
    return found(db.attempt(owner(), id));
  }

  public Page<Attempt> attempts(UUID task, int p, int s) {
    learning.task(task);
    page(p, s);
    return new Page<>(db.attempts(owner(), task, s, p * s), db.attemptCount(owner(), task), p, s);
  }

  public Attempt submit(UUID task, Submit v, String key) {
    lock();
    key(key);
    var t = learning.task(task);
    var q = secret(v.questionId());
    require(q.nodeId().equals(t.nodeId()), "QUESTION_NODE_MISMATCH", "题目不属于此学习节点");
    String hash =
        hash(
            task
                + "\n"
                + v.questionId()
                + "\n"
                + v.content()
                + "\n"
                + v.declaredIndependent()
                + "\n"
                + v.attachmentIds());
    var old = db.attemptKey(owner(), key);
    if (old != null) {
      same(old.inputHash().equals(hash));
      return attempt(old.id());
    }
    require(q.status().equals("ACTIVE"), "QUESTION_ARCHIVED", "题目已归档");
    require(!v.content().isBlank() || !v.attachmentIds().isEmpty(), "EMPTY_ATTEMPT", "请提交文字或图片作答");
    require(
        new HashSet<>(v.attachmentIds()).size() == v.attachmentIds().size(),
        "DUPLICATE_ATTACHMENT",
        "图片引用不能重复");
    for (UUID a : v.attachmentIds()) {
      var file = found(db.storedAttachment(owner(), a));
      require(
          file.taskId().equals(task) && file.status().equals("READY"),
          "ATTACHMENT_UNAVAILABLE",
          "图片不可用或不属于此任务");
    }
    int level = db.exposure(owner(), q.id());
    UUID id = UUID.randomUUID();
    db.attemptInsert(
        owner(),
        id,
        task,
        t.nodeId(),
        q.id(),
        v.content(),
        v.declaredIndependent() && level == 0,
        level,
        key,
        hash);
    for (UUID a : v.attachmentIds()) db.attemptAttachment(owner(), id, a);
    var after = attempt(id);
    audit.record("learning_attempt", id, "SUBMIT", null, after);
    return after;
  }

  public record AttemptDetail(
      Attempt attempt, Question question, List<Attachment> attachments, int currentHintLevel) {}

  public AttemptDetail attemptDetail(UUID id) {
    var a = attempt(id);
    return new AttemptDetail(
        a,
        secret(a.questionId()).view(),
        db.attemptAttachments(owner(), id),
        db.exposure(owner(), a.questionId()));
  }

  public Map<String, Object> hint(UUID id, Hint v) {
    lock();
    var a = attempt(id);
    var q = secret(a.questionId());
    boolean full = v.level().equals("FULL_SOLUTION");
    require(!full || v.confirmed(), "CONFIRMATION_REQUIRED", "查看完整解析会记录答案曝光，请明确确认");
    String text = full ? q.answerKey() : q.hintText();
    require(!text.isBlank(), "HINT_UNAVAILABLE", full ? "此题尚未设置解析" : "此题尚未设置一步提示");
    db.expose(owner(), q.id(), full ? 2 : 1);
    audit.record(
        "question", q.id(), "HINT_EXPOSED", null, Map.of("level", v.level(), "attemptId", id));
    return Map.of("level", v.level(), "text", text, "source", "AUTHOR_PREPARED");
  }

  private Review insertReview(
      Attempt a, UUID turn, String verdict, String type, String feedback, String key) {
    key(key);
    String hash = hash(a.id() + "\n" + turn + "\n" + verdict + "\n" + type + "\n" + feedback);
    var old = db.reviewKey(owner(), key);
    if (old != null) {
      same(old.inputHash().equals(hash));
      return found(db.review(owner(), old.id()));
    }
    UUID id = UUID.randomUUID();
    db.reviewInsert(owner(), id, a.id(), turn, verdict, type, feedback, key, hash);
    var after = found(db.review(owner(), id));
    audit.record("attempt_review", id, "APPEND", null, after);
    return after;
  }

  private static String normal(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ");
  }

  public Review check(UUID id, String key) {
    lock();
    var a = attempt(id);
    var q = secret(a.questionId());
    boolean exact = q.questionType().equals("EXACT_TEXT") && !a.submittedContent().isBlank();
    String result =
        exact
            ? (normal(a.submittedContent()).equals(normal(q.answerKey())) ? "CORRECT" : "INCORRECT")
            : "UNSURE";
    return insertReview(
        a,
        null,
        result,
        "RULE",
        exact ? "仅按保存答案作规范化文本比对，不代表证明或作品质量判定。" : "证明、作品或纯图片作答需要人工复核，当前结果不确定。",
        key);
  }

  public Review review(UUID id, ReviewWrite v, String key) {
    lock();
    var a = attempt(id);
    require(v.confirmed(), "CONFIRMATION_REQUIRED", "请确认这是人工复核意见");
    if (v.tutorTurnId() != null)
      require(
          db.reviewedTurn(owner(), v.tutorTurnId(), id),
          "INVALID_TUTOR_REVIEW",
          "AI建议必须是此作答已完成的本人对话");
    return insertReview(
        a,
        v.tutorTurnId(),
        v.verdict(),
        v.tutorTurnId() == null ? "SELF_REVIEW" : "REVIEWED_AI",
        v.feedback(),
        key);
  }

  public Page<Review> reviews(UUID id, int p, int s) {
    attempt(id);
    page(p, s);
    return new Page<>(db.reviews(owner(), id, s, p * s), db.reviewCount(owner(), id), p, s);
  }

  public Evidence confirmEvidence(UUID review, boolean confirmed) {
    lock();
    require(confirmed, "CONFIRMATION_REQUIRED", "请确认把本次复核记录为证据");
    var r = found(db.review(owner(), review));
    var a = attempt(r.attemptId());
    var existing = db.reviewEvidence(owner(), review);
    if (existing != null) return existing;
    UUID id = UUID.randomUUID();
    db.evidenceInsert(
        owner(),
        id,
        a.nodeId(),
        a.id(),
        review,
        r.reviewerType().equals("RULE") ? "RULE_RESULT" : r.reviewerType(),
        r.reviewerType().equals("RULE") ? "RULE_CHECKED" : "SELF_REPORTED",
        r.verdict(),
        a.independent());
    var after = found(db.evidence(owner(), id));
    audit.record("learning_evidence", id, "CONFIRM", null, after);
    return after;
  }

  public Page<Evidence> evidence(UUID task, int p, int s) {
    var t = learning.task(task);
    page(p, s);
    return new Page<>(
        db.evidenceList(owner(), t.nodeId(), s, p * s),
        db.evidenceCount(owner(), t.nodeId()),
        p,
        s);
  }

  public AssessmentView assess(UUID task, Assess v) {
    lock();
    var t = learning.task(task);
    require(
        new HashSet<>(v.evidenceIds()).size() == v.evidenceIds().size(),
        "DUPLICATE_EVIDENCE",
        "证据不能重复");
    List<Evidence> evidence =
        v.evidenceIds().stream().map(id -> found(db.evidence(owner(), id))).toList();
    require(
        evidence.stream().allMatch(e -> e.nodeId().equals(t.nodeId())),
        "EVIDENCE_NODE_MISMATCH",
        "证据不属于此节点");
    int level = v.level();
    String confidence = "LOW", reason = v.reason();
    if (v.assessorType().equals("RULE")) {
      long distinct =
          evidence.stream()
              .filter(
                  e ->
                      e.verification().equals("RULE_CHECKED")
                          && e.outcome().equals("CORRECT")
                          && e.independent())
              .map(e -> attempt(e.attemptId()).questionId())
              .distinct()
              .count();
      boolean aided =
          evidence.stream()
              .anyMatch(
                  e -> e.verification().equals("RULE_CHECKED") && e.outcome().equals("CORRECT"));
      level = distinct >= 2 ? 2 : (distinct == 1 || aided ? 1 : 0);
      confidence = distinct >= 2 ? "MEDIUM" : "LOW";
      reason =
          "规则v1：自报独立且规则文本比对正确的不同题数="
              + distinct
              + "；提示后规则正确="
              + aided
              + "。最多L2，不证明实际应用能力。用户说明："
              + v.reason();
    }
    UUID id = UUID.randomUUID();
    require(reason.length() <= 8000, "ASSESSMENT_REASON_TOO_LONG", "请缩短说明，以保留完整规则依据");
    db.assessmentInsert(owner(), id, t.nodeId(), level, confidence, v.assessorType(), reason);
    for (UUID e : v.evidenceIds()) db.assessmentLink(owner(), id, t.nodeId(), e);
    var a =
        db.assessments(owner(), t.nodeId(), 1, 0).stream()
            .filter(row -> row.id().equals(id))
            .findFirst()
            .orElseThrow();
    audit.record("mastery_assessment", id, "APPEND", null, a);
    return new AssessmentView(a, List.copyOf(v.evidenceIds()));
  }

  public Page<AssessmentView> assessments(UUID task, int p, int s) {
    var t = learning.task(task);
    page(p, s);
    return new Page<>(
        db.assessments(owner(), t.nodeId(), s, p * s).stream()
            .map(a -> new AssessmentView(a, db.assessmentEvidence(owner(), a.id())))
            .toList(),
        db.assessmentCount(owner(), t.nodeId()),
        p,
        s);
  }
}
