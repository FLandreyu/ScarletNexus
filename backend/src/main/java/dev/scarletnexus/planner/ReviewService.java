package dev.scarletnexus.planner;

import static dev.scarletnexus.common.DomainFailure.require;

import dev.scarletnexus.assessment.PracticeMapper;
import dev.scarletnexus.common.AuditLog;
import dev.scarletnexus.learning.*;
import dev.scarletnexus.planner.PlannerModels.*;
import dev.scarletnexus.skill.CatalogMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReviewService {
  private final ReviewMapper db;
  private final PlanningSupport s;
  private final LearningService learning;
  private final LearningMapper tasks;
  private final PracticeMapper practice;
  private final CatalogMapper catalog;
  private final AuditLog audit;

  public ReviewService(
      ReviewMapper db,
      PlanningSupport s,
      LearningService learning,
      LearningMapper tasks,
      PracticeMapper practice,
      CatalogMapper catalog,
      AuditLog audit) {
    this.db = db;
    this.s = s;
    this.learning = learning;
    this.tasks = tasks;
    this.practice = practice;
    this.catalog = catalog;
    this.audit = audit;
  }

  public Schedule schedule(UUID id) {
    return s.found(db.schedule(s.owner(), id));
  }

  public ReviewView view(UUID id) {
    var r = schedule(id);
    return new ReviewView(
        r,
        s.found(catalog.node(s.owner(), r.nodeId())).title(),
        db.cycleTask(s.owner(), id, r.cycleNumber()));
  }

  public LearningModels.Page<ReviewView> list(boolean due, Instant until, int page, int size) {
    s.page(page, size);
    return new LearningModels.Page<>(
        db.schedules(s.owner(), due, until, size, page * size).stream()
            .map(r -> view(r.id()))
            .toList(),
        db.count(s.owner(), due, until),
        page,
        size);
  }

  public ReviewView create(ReviewController.Create v) {
    s.lock();
    s.zone(v.timeZone());
    var t = learning.task(v.taskId());
    require(
        db.forNode(s.owner(), t.nodeId()) == null, "REVIEW_ALREADY_EXISTS", "此节点已有复习安排，请调整现有安排");
    require(
        s.found(catalog.skill(s.owner(), t.skillId())).status().equals("ACTIVE")
            && s.found(catalog.node(s.owner(), t.nodeId())).status().equals("ACTIVE"),
        "ARCHIVED_REVIEW_SOURCE",
        "请先恢复技能和节点");
    int interval = 1;
    LocalDate date = v.firstDate();
    String why = "手动首次日期；" + v.reason().strip();
    if (v.evidenceId() != null) {
      var e = s.found(practice.evidence(s.owner(), v.evidenceId()));
      require(e.nodeId().equals(t.nodeId()), "REVIEW_EVIDENCE_MISMATCH", "证据节点不匹配");
      interval = e.outcome().equals("CORRECT") ? 3 : 1;
      why = "首次证据：" + e.outcome() + "，间隔" + interval + "天；" + v.reason().strip();
      if (date == null)
        date = e.observedAt().atZone(s.zone(v.timeZone())).toLocalDate().plusDays(interval);
    }
    require(date != null, "REVIEW_FIRST_DATE_REQUIRED", "请选择首次复习日期或确认的证据");
    UUID id = UUID.randomUUID();
    Instant due = s.due(date, v.timeZone());
    db.insert(
        s.owner(),
        id,
        t.skillId(),
        t.nodeId(),
        t.id(),
        v.evidenceId(),
        v.plannedMinutes(),
        v.timeZone(),
        due,
        interval,
        why);
    event(id, "CREATE", null, null, null, null, null, null, null, null, due, null, interval, why);
    audit.record("review_schedule", id, "CREATE", null, schedule(id));
    return view(id);
  }

  private void event(
      UUID id,
      String kind,
      String key,
      String hash,
      UUID evidence,
      UUID attempt,
      String outcome,
      String verification,
      Boolean independent,
      Instant before,
      Instant after,
      Integer previous,
      int next,
      String reason) {
    db.event(
        s.owner(),
        UUID.randomUUID(),
        id,
        kind,
        key,
        hash,
        evidence,
        attempt,
        outcome,
        verification,
        independent,
        before,
        after,
        previous,
        next,
        reason);
  }

  public ReviewView adjust(UUID id, ReviewController.Adjust v) {
    s.lock();
    var before = schedule(id);
    s.conflict(before.revision() == v.revision());
    Instant due = s.due(v.nextDate(), before.timeZone());
    db.adjust(s.owner(), id, due, v.status(), v.reason().strip());
    event(
        id,
        "ADJUST",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        before.nextReviewAt(),
        due,
        before.intervalDays(),
        before.intervalDays(),
        v.reason().strip());
    audit.record("review_schedule", id, "ADJUST", before, schedule(id));
    return view(id);
  }

  public ReviewView complete(UUID id, ReviewController.Complete v, String key) {
    s.lock();
    s.key(key);
    String hash = s.hash(List.of(id, v));
    var replay = db.replay(s.owner(), key);
    if (replay != null) {
      s.conflict(replay.scheduleId().equals(id) && replay.inputHash().equals(hash));
      return view(id);
    }
    var before = schedule(id);
    s.conflict(before.revision() == v.revision());
    require(before.status().equals("ACTIVE"), "REVIEW_PAUSED", "请先恢复复习安排");
    var e = s.found(practice.evidence(s.owner(), v.evidenceId()));
    var a = s.found(practice.attempt(s.owner(), e.attemptId()));
    require(e.nodeId().equals(before.nodeId()), "REVIEW_EVIDENCE_MISMATCH", "证据节点不匹配");
    Instant boundary =
        before.lastCompletedAt() == null ? before.createdAt() : before.lastCompletedAt();
    require(
        a.createdAt().isAfter(boundary) && !db.used(s.owner(), e.id()),
        "REVIEW_FRESH_ATTEMPT_REQUIRED",
        "需要安排建立或上次复测之后的新作答与新确认的证据");
    UUID cycle = db.cycleTask(s.owner(), id, before.cycleNumber());
    if (cycle != null) s.idle(cycle, tasks);
    boolean reliable =
        e.outcome().equals("CORRECT") && e.independent() && e.verification().equals("RULE_CHECKED");
    int interval = reliable ? Math.min(365, before.intervalDays() * 2) : 1;
    Instant now = Instant.now(),
        due =
            s.due(
                now.atZone(s.zone(before.timeZone())).toLocalDate().plusDays(interval),
                before.timeZone());
    String why =
        "规则v1："
            + e.outcome()
            + " / "
            + e.verification()
            + " / "
            + (e.independent() ? "独立" : "非独立")
            + "；"
            + (reliable ? "间隔翻倍" : "保守回到1天")
            + "；"
            + v.reason().strip();
    db.complete(s.owner(), id, due, interval, now, why);
    event(
        id,
        "RETEST",
        key,
        hash,
        e.id(),
        a.id(),
        e.outcome(),
        e.verification(),
        e.independent(),
        before.nextReviewAt(),
        due,
        before.intervalDays(),
        interval,
        why);
    if (cycle != null) {
      var t = learning.task(cycle);
      if (!Set.of("DONE", "CANCELLED").contains(t.status())) {
        tasks.updateTask(s.owner(), t.id(), t.title(), t.plannedMinutes(), t.dueDate(), "DONE");
        audit.record("learning_task", t.id(), "RETEST", t, learning.task(t.id()));
      }
    }
    audit.record("review_schedule", id, "RETEST", before, schedule(id));
    return view(id);
  }

  public LearningModels.Page<ReviewEvent> history(UUID id, int page, int size) {
    schedule(id);
    s.page(page, size);
    return new LearningModels.Page<>(
        db.history(s.owner(), id, size, page * size), db.historyCount(s.owner(), id), page, size);
  }

  public UUID materialize(UUID id) {
    var r = schedule(id);
    UUID existing = db.cycleTask(s.owner(), id, r.cycleNumber());
    if (existing != null) return existing;
    var original = learning.task(r.originalTaskId());
    String title = "复习：" + s.found(catalog.node(s.owner(), r.nodeId())).title();
    if (title.length() > 160) title = title.substring(0, 160);
    var t =
        learning.createTask(
            new LearningController.TaskCreate(
                r.skillId(),
                original.goalId(),
                r.nodeId(),
                null,
                null,
                title,
                r.plannedMinutes(),
                null));
    db.cycleTaskInsert(s.owner(), id, r.cycleNumber(), r.nodeId(), t.id());
    return t.id();
  }
}
