package dev.scarletnexus.ai;

import com.fasterxml.jackson.databind.*;
import dev.scarletnexus.common.*;
import dev.scarletnexus.identity.CurrentUser;
import dev.scarletnexus.knowledge.*;
import dev.scarletnexus.skill.CatalogMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Internal orchestration; intentionally no HTTP generation endpoint until monetary budgets exist.
 */
@Service
public class GenerationJobs {
  public record Job(
      UUID id,
      UUID skillId,
      String inputHash,
      LocalDate budgetDay,
      long reservedTokens,
      String state,
      String usageSource,
      Long chargedTokens,
      String errorCode,
      UUID draftId) {}

  @Mapper
  public interface Store {
    @Select(
        "SELECT id,skill_id,state,usage_source,reserved_tokens,charged_tokens,error_code,draft_id,created_at,(state='RUNNING' AND created_at<=now()-interval '5 minutes') AS recoverable FROM ai_generation_jobs WHERE owner_id=#{owner} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<View> list(UUID owner, int size, int offset);

    @Select(
        "SELECT id,skill_id,state,usage_source,reserved_tokens,charged_tokens,error_code,draft_id,created_at,(state='RUNNING' AND created_at<=now()-interval '5 minutes') AS recoverable FROM ai_generation_jobs WHERE owner_id=#{owner} AND id=#{id}")
    View view(UUID owner, UUID id);

    @Select(
        "SELECT id,skill_id,input_hash,budget_day,reserved_tokens,state,usage_source,charged_tokens,error_code,draft_id FROM ai_generation_jobs WHERE owner_id=#{owner} AND id=#{id}")
    Job byId(UUID owner, UUID id);

    @Select(
        "SELECT id,skill_id,input_hash,budget_day,reserved_tokens,state,usage_source,charged_tokens,error_code,draft_id FROM ai_generation_jobs WHERE owner_id=#{owner} AND request_key=#{key}")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Job find(UUID owner, String key);

    @Insert(
        "INSERT INTO ai_daily_budgets(owner_id,budget_day) VALUES(#{owner},#{day}) ON CONFLICT DO NOTHING")
    void day(UUID owner, LocalDate day);

    @Update(
        "UPDATE ai_daily_budgets SET reserved_tokens=reserved_tokens+#{amount} WHERE owner_id=#{owner} AND budget_day=#{day} AND reserved_tokens+consumed_tokens<=#{limit}-#{amount}")
    int reserve(UUID owner, LocalDate day, long amount, long limit);

    @Insert(
        "INSERT INTO ai_generation_jobs(id,owner_id,skill_id,request_key,input_hash,budget_day,reserved_tokens,state,draft_id) VALUES(#{id},#{owner},#{skill},#{key},#{hash},#{day},#{amount},'RUNNING',#{draft})")
    void insert(
        UUID id,
        UUID owner,
        UUID skill,
        String key,
        String hash,
        LocalDate day,
        long amount,
        UUID draft);

    @Update(
        "UPDATE ai_daily_budgets SET reserved_tokens=reserved_tokens-#{reserved},consumed_tokens=consumed_tokens+#{charged} WHERE owner_id=#{owner} AND budget_day=#{day}")
    void settle(UUID owner, LocalDate day, long reserved, long charged);

    @Update(
        "UPDATE ai_generation_jobs SET state=#{state},usage_source=#{source},charged_tokens=#{charged},error_code=#{error},finished_at=now() WHERE owner_id=#{owner} AND id=#{id} AND state='RUNNING'")
    void finish(UUID owner, UUID id, String state, String source, long charged, String error);
  }

  private final Store db;
  private final CatalogMapper catalog;
  private final DraftService drafts;
  private final GraphValidator validator;
  private final ObjectMapper json;
  private final AuditLog audit;
  private final TransactionTemplate tx;
  private final MoneyLedger money;

  public GenerationJobs(
      Store db,
      CatalogMapper catalog,
      DraftService drafts,
      GraphValidator validator,
      ObjectMapper json,
      AuditLog audit,
      PlatformTransactionManager manager,
      MoneyLedger money) {
    this.money = money;
    this.db = db;
    this.catalog = catalog;
    this.drafts = drafts;
    this.validator = validator;
    this.json = json;
    this.audit = audit;
    tx = new TransactionTemplate(manager);
  }

  public Job get(String key) {
    Job j = db.find(CurrentUser.id(), key);
    if (j == null) throw DomainFailure.missing();
    return j;
  }

  public record View(
      UUID id,
      UUID skillId,
      String state,
      String usageSource,
      long reservedTokens,
      Long chargedTokens,
      String errorCode,
      UUID draftId,
      java.time.OffsetDateTime createdAt,
      boolean recoverable) {}

  public List<View> list(int page, int size) {
    DomainFailure.require(
        page >= 0 && page <= 10000 && size >= 1 && size <= 100, "INVALID_PAGE", "分页参数无效");
    return db.list(CurrentUser.id(), size, page * size);
  }

  public View view(UUID id) {
    View v = db.view(CurrentUser.id(), id);
    if (v == null) throw DomainFailure.missing();
    return v;
  }

  public View recover(UUID id, boolean confirmed) {
    DomainFailure.require(confirmed, "CONFIRMATION_REQUIRED", "请确认结束任务且保留预算占用");
    return tx.execute(
        s -> {
          UUID owner = CurrentUser.id();
          catalog.lockOwner(owner);
          View v = view(id);
          if (!"RUNNING".equals(v.state())) return v;
          if (!v.recoverable())
            throw new DomainFailure(
                org.springframework.http.HttpStatus.CONFLICT,
                "AI_JOB_STILL_ACTIVE",
                "任务未超过五分钟，请稍后刷新");
          Job j = db.byId(owner, id);
          db.settle(owner, j.budgetDay(), j.reservedTokens(), j.reservedTokens());
          money.settle(owner, id, null, false);
          db.finish(owner, id, "FAILED", "UNKNOWN", j.reservedTokens(), "AI_INTERRUPTED_UNKNOWN");
          audit.record(
              "AI_JOB",
              id,
              "RECOVERED",
              null,
              Map.of("chargedTokens", j.reservedTokens(), "usageSource", "UNKNOWN"));
          return view(id);
        });
  }

  // Budget limits and reservations must come from trusted server policy, never request JSON.
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  public Job generate(
      UUID skill,
      String key,
      String input,
      long reservation,
      long dailyLimit,
      AIProvider provider) {
    DomainFailure.require(
        key != null
            && key.matches("[A-Za-z0-9._-]{1,128}")
            && input != null
            && input.length() <= 50000
            && reservation > 0
            && dailyLimit > 0
            && reservation <= dailyLimit
            && dailyLimit <= 1_000_000_000L,
        "AI_INVALID_REQUEST",
        "生成参数或预算无效");
    UUID owner = CurrentUser.id();
    String hash;
    try {
      hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest((skill + "\n" + input).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot fingerprint generation");
    }
    boolean[] created = {false};
    Job job =
        tx.execute(
            s -> {
              catalog.lockOwner(owner);
              Job old = db.find(owner, key);
              if (old != null) {
                DomainFailure.require(
                    old.inputHash().equals(hash), "AI_KEY_CONFLICT", "幂等键已用于其他输入");
                return old;
              }
              LocalDate day = LocalDate.now(ZoneOffset.UTC);
              db.day(owner, day);
              DomainFailure.require(
                  db.reserve(owner, day, reservation, dailyLimit) == 1,
                  "AI_BUDGET_EXCEEDED",
                  "当日生成预算不足");
              var draft = drafts.create(skill);
              UUID id = UUID.randomUUID();
              db.insert(id, owner, skill, key, hash, day, reservation, draft.id());
              money.reserve(owner, id, day);
              audit.record("AI_JOB", id, "STARTED", null, Map.of("reservedTokens", reservation));
              created[0] = true;
              return db.find(owner, key);
            });
    if (!created[0]) return job;
    AIProvider.Result result;
    try {
      JsonNode schema =
          json.readTree(getClass().getResourceAsStream("/schemas/graph-draft-v1.schema.json"));
      result =
          provider.generateStructured(
              "Return a knowledge graph JSON candidate only. Use canonical UUID identifiers. Schema version is 1. Never publish data.",
              input,
              schema);
    } catch (Exception failure) {
      String code = failure instanceof AIProvider.Failure f ? f.code() : "AI_INTERNAL_FAILURE";
      finish(job, key, null, code);
      return get(key);
    }
    finish(job, key, result, null);
    return get(key);
  }

  private void finish(Job job, String key, AIProvider.Result result, String failure) {
    tx.executeWithoutResult(
        s -> {
          UUID owner = CurrentUser.id();
          catalog.lockOwner(owner);
          if (!"RUNNING".equals(get(key).state())) return;
          long charged = job.reservedTokens();
          String source = "UNKNOWN";
          if ("AI_NOT_CONFIGURED".equals(failure)) {
            charged = 0;
            source = "NOT_SENT";
          }
          if (result != null
              && result.usage() != null
              && result.usage().inputTokens() != null
              && result.usage().outputTokens() != null) {
            try {
              long n = Math.addExact(result.usage().inputTokens(), result.usage().outputTokens());
              if (result.usage().inputTokens() >= 0
                  && result.usage().outputTokens() >= 0
                  && n <= 1_000_000_000L) {
                charged = n;
                source = "PROVIDER";
              }
            } catch (ArithmeticException ignored) {
            }
          }
          String error = failure == null && result == null ? "AI_INVALID_OUTPUT" : failure;
          if (result != null) {
            try {
              if (!validator.validate(validator.parse(result.document()), List.of()).isEmpty())
                error = "AI_INVALID_GRAPH";
            } catch (Exception e) {
              error = "AI_INVALID_GRAPH";
            }
            var target = drafts.get(job.draftId());
            if (error == null && (target.revision() != 0 || !"EDITING".equals(target.status())))
              error = "AI_DRAFT_CONFLICT";
            if (error == null) drafts.save(job.draftId(), 0, result.document());
          }
          db.settle(owner, job.budgetDay(), job.reservedTokens(), charged);
          money.settle(
              owner,
              job.id(),
              result == null ? null : result.usage(),
              "AI_NOT_CONFIGURED".equals(failure));
          db.finish(
              owner, job.id(), error == null ? "SUCCEEDED" : "FAILED", source, charged, error);
          audit.record(
              "AI_JOB",
              job.id(),
              error == null ? "SUCCEEDED" : "FAILED",
              null,
              Map.of("usageSource", source, "chargedTokens", charged));
        });
  }
}
