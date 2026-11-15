package dev.scarletnexus.planner;

import static dev.scarletnexus.common.DomainFailure.require;

import dev.scarletnexus.common.AuditLog;
import dev.scarletnexus.learning.*;
import dev.scarletnexus.planner.PlannerModels.*;
import dev.scarletnexus.skill.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PlannerService {
  private final PlannerMapper db;
  private final ReviewMapper reviews;
  private final ReviewService reviewService;
  private final PlanningSupport s;
  private final CatalogMapper catalog;
  private final LearningService learning;
  private final AuditLog audit;

  public PlannerService(
      PlannerMapper db,
      ReviewMapper reviews,
      ReviewService reviewService,
      PlanningSupport s,
      CatalogMapper catalog,
      LearningService learning,
      AuditLog audit) {
    this.db = db;
    this.reviews = reviews;
    this.reviewService = reviewService;
    this.s = s;
    this.catalog = catalog;
    this.learning = learning;
    this.audit = audit;
  }

  private record Source(String hash, List<Candidate> candidates) {}

  private Source source(LocalDate date, String zone, List<SkillLimit> limits) {
    limits = limits.stream().sorted(Comparator.comparing(SkillLimit::skillId)).toList();
    var ids = limits.stream().map(SkillLimit::skillId).sorted().toList();
    var rows = db.candidates(s.owner(), ids);
    var rs = reviews.candidates(s.owner(), ids);
    require(rows.size() <= 2000 && rs.size() <= 2000, "PLAN_SOURCE_TOO_LARGE", "候选超过2000项，请缩小技能范围");
    var done = db.doneNodes(s.owner());
    var relations =
        catalog.relations(s.owner()).stream()
            .sorted(Comparator.comparing(CatalogModels.Relation::id))
            .toList();
    var positions = new ArrayList<Object>();
    var attached = new HashMap<UUID, Set<UUID>>();
    for (UUID id : ids) {
      var skill = s.found(catalog.skill(s.owner(), id));
      var ps =
          catalog.positions(s.owner(), id).stream()
              .sorted(Comparator.comparing(CatalogModels.Position::nodeId))
              .toList();
      positions.add(List.of(skill, ps));
      attached.put(id, new HashSet<>(ps.stream().map(CatalogModels.Position::nodeId).toList()));
    }
    var candidates = new ArrayList<Candidate>();
    for (var r : rows) {
      String blocked = null;
      if (!r.skillStatus().equals("ACTIVE")
          || !r.nodeStatus().equals("ACTIVE")
          || !attached.get(r.skillId()).contains(r.nodeId())) blocked = "技能/节点归档或已移出技能";
      else if (r.goalStatus() != null && !r.goalStatus().equals("ACTIVE")) blocked = "所属目标已完成或归档";
      else if (r.status().equals("DEFERRED") && r.dueDate() != null && r.dueDate().isAfter(date))
        blocked = "已延期至" + r.dueDate();
      else if (relations.stream()
          .anyMatch(
              e ->
                  e.relationType().equals("PREREQUISITE_OF")
                      && e.targetNodeId().equals(r.nodeId())
                      && !done.contains(e.sourceNodeId()))) blocked = "先修节点尚未完成（以任务/路线自报完成为依据）";
      LocalDate deadline =
          r.dueDate() == null
              ? r.goalDeadline()
              : r.goalDeadline() == null
                  ? r.dueDate()
                  : r.dueDate().isBefore(r.goalDeadline()) ? r.dueDate() : r.goalDeadline();
      boolean continuing = r.status().equals("DOING");
      int weakness =
          Set.of("INCORRECT", "UNSURE").contains(r.latestOutcome() == null ? "" : r.latestOutcome())
              ? 1
              : 0;
      String reason =
          blocked == null
              ? (continuing ? "继续进行中任务；" : "")
                  + "目标优先级"
                  + r.priority()
                  + "；截止"
                  + (deadline == null ? "未设置" : deadline)
                  + "；近期结果"
                  + (r.latestOutcome() == null ? "暂无" : r.latestOutcome())
              : blocked;
      candidates.add(
          new Candidate(
              "LEARN",
              r.id(),
              r.id(),
              null,
              r.skillId(),
              r.nodeId(),
              r.title(),
              r.plannedMinutes(),
              r.revision(),
              null,
              blocked == null,
              reason,
              r.priority(),
              deadline,
              continuing,
              weakness,
              r.createdAt()));
    }
    Instant cutoff = date.plusDays(1).atStartOfDay(s.zone(zone)).toInstant();
    for (var r : rs) {
      String blocked = null;
      if (!r.status().equals("ACTIVE")) blocked = "复习已暂停";
      else if (!r.skillStatus().equals("ACTIVE")
          || !r.nodeStatus().equals("ACTIVE")
          || !r.attached()) blocked = "复习节点不可用";
      else if (!r.nextReviewAt().isBefore(cutoff)) blocked = "未到复习日期";
      else if (r.taskStatus() != null && Set.of("DONE", "CANCELLED").contains(r.taskStatus()))
        blocked = "本周期任务已结束，请提交复测证据后进入下一周期";
      candidates.add(
          new Candidate(
              "REVIEW",
              r.id(),
              r.taskId(),
              r.id(),
              r.skillId(),
              r.nodeId(),
              "复习：" + r.title().substring(0, Math.min(157, r.title().length())),
              r.plannedMinutes(),
              r.taskRevision(),
              r.revision(),
              blocked == null,
              blocked == null ? "到期复习" + r.nextReviewAt() + "，优先处理" : blocked,
              0,
              r.nextReviewAt().atZone(s.zone(zone)).toLocalDate(),
              false,
              0,
              r.createdAt()));
    }
    var day = db.day(s.owner(), date);
    return new Source(
        s.hash(
            List.of(
                date,
                zone,
                limits,
                rows,
                rs,
                done,
                relations,
                positions,
                day == null ? 0 : day.revision())),
        candidates);
  }

  public DraftView create(PlannerController.Create v) {
    s.lock();
    s.zone(v.timeZone());
    date(v.planDate());
    require(
        v.skillLimits().stream().allMatch(l -> l.skillId() != null),
        "INVALID_SKILL_BUDGET",
        "请选择技能");
    var limits =
        v.skillLimits().stream().sorted(Comparator.comparing(SkillLimit::skillId)).toList();
    require(
        limits.stream().map(SkillLimit::skillId).distinct().count() == limits.size(),
        "DUPLICATE_SKILL_LIMIT",
        "技能预算不能重复");
    for (var l : limits) {
      require(
          l.skillId() != null && l.maxMinutes() >= 0 && l.maxMinutes() <= 1440,
          "INVALID_SKILL_BUDGET",
          "技能预算须为0至1440分钟");
      s.found(catalog.skill(s.owner(), l.skillId()));
    }
    var source = source(v.planDate(), v.timeZone(), limits);
    UUID id = UUID.randomUUID();
    db.draftInsert(s.owner(), id, v.planDate(), v.timeZone(), v.budgetMinutes(), source.hash());
    for (var l : limits) db.draftLimit(s.owner(), id, l);
    for (var d : PlanningRules.generate(source.candidates(), v.budgetMinutes(), limits))
      db.draftItem(
          s.owner(), UUID.randomUUID(), id, d.candidate(), d.selected(), d.order(), d.reason());
    var after = view(id);
    audit.record("plan_draft", id, "CREATE", null, after);
    return after;
  }

  private void date(LocalDate d) {
    require(d.getYear() >= 2000 && d.getYear() <= 2200, "INVALID_PLAN_DATE", "计划日期应在2000至2200年");
  }

  private Draft draft(UUID id) {
    return s.found(db.draft(s.owner(), id));
  }

  public DraftView view(UUID id) {
    var d = draft(id);
    var limits = db.draftLimits(s.owner(), id);
    var items = db.draftItems(s.owner(), id);
    return new DraftView(
        d,
        limits,
        items,
        items.stream().filter(DraftItem::selected).mapToInt(DraftItem::minutes).sum(),
        d.state().equals("DRAFT")
            && !d.sourceHash().equals(source(d.planDate(), d.timeZone(), limits).hash()));
  }

  public LearningModels.Page<Draft> drafts(LocalDate date, int page, int size) {
    s.page(page, size);
    return new LearningModels.Page<>(
        db.drafts(s.owner(), date, size, page * size), db.draftCount(s.owner(), date), page, size);
  }

  public DraftView choose(UUID id, PlannerController.Choose v) {
    s.lock();
    var before = view(id);
    s.conflict(
        before.draft().state().equals("DRAFT")
            && before.draft().revision() == v.revision()
            && !before.sourceChanged());
    var byId = new HashMap<UUID, DraftItem>();
    for (var item : before.items()) byId.put(item.id(), item);
    require(
        new HashSet<>(v.itemIds()).size() == v.itemIds().size(), "DUPLICATE_PLAN_ITEM", "不能重复选择");
    var selected = v.itemIds().stream().map(i -> s.found(byId.get(i))).toList();
    PlanningRules.validate(selected, before.draft().budgetMinutes(), before.skillLimits());
    int order = 0;
    for (var item : selected)
      db.chooseItem(s.owner(), item.id(), true, order++, "手动选择；" + baseReason(item));
    for (var item : before.items())
      if (!v.itemIds().contains(item.id()))
        db.chooseItem(s.owner(), item.id(), false, order++, "手动未选；" + baseReason(item));
    db.draftState(s.owner(), id, "DRAFT");
    var after = view(id);
    audit.record("plan_draft", id, "CHOOSE", before, after);
    return after;
  }

  private String baseReason(DraftItem item) {
    return item.reason().length() > 4000 ? item.reason().substring(0, 4000) : item.reason();
  }

  public PlanView confirm(UUID id, PlannerController.Confirm v, String key) {
    s.lock();
    s.key(key);
    require(v.confirmed(), "EXPLICIT_CONFIRMATION_REQUIRED", "必须明确确认计划");
    String hash = s.hash(List.of(id, v));
    var replay = db.confirmation(s.owner(), key);
    if (replay != null) {
      s.conflict(replay.inputHash().equals(hash));
      return plan(replay.planId());
    }
    var before = view(id);
    var d = before.draft();
    s.conflict(
        d.state().equals("DRAFT") && d.revision() == v.revision() && !before.sourceChanged());
    var day = db.day(s.owner(), d.planDate());
    s.conflict((day == null ? 0 : day.revision()) == v.dayRevision());
    var selected = before.items().stream().filter(DraftItem::selected).toList();
    PlanningRules.validate(selected, d.budgetMinutes(), before.skillLimits());
    UUID pid = UUID.randomUUID();
    db.planInsert(s.owner(), pid, d, db.nextVersion(s.owner(), d.planDate()));
    for (var l : before.skillLimits()) db.planLimit(s.owner(), pid, l);
    int order = 0;
    for (var item : selected) {
      UUID task =
          item.kind().equals("REVIEW") ? reviewService.materialize(item.reviewId()) : item.taskId();
      var t = learning.task(task);
      require(
          t.plannedMinutes() == item.minutes(), "REVIEW_TASK_MINUTES_CHANGED", "复习任务预计时长已变化，请重新安排");
      db.planItem(s.owner(), UUID.randomUUID(), pid, task, item, t.revision(), order++);
    }
    db.dayInsert(s.owner(), UUID.randomUUID(), d.planDate());
    db.daySet(s.owner(), d.planDate(), pid);
    db.draftState(s.owner(), id, "CONFIRMED");
    db.confirmationInsert(s.owner(), key, hash, pid);
    audit.record("daily_plan", pid, "CONFIRM", null, plan(pid));
    return plan(pid);
  }

  public PlanView plan(UUID id) {
    var p = s.found(db.plan(s.owner(), id));
    var items =
        db.planItems(s.owner(), id).stream()
            .map(
                i -> {
                  var t = learning.task(i.taskId());
                  return new PlanItemView(i, t, i.taskRevision() != t.revision());
                })
            .toList();
    return new PlanView(
        p,
        db.planLimits(s.owner(), id),
        items,
        items.stream().mapToInt(i -> i.item().plannedMinutes()).sum());
  }

  public DayView day(LocalDate date) {
    var d = db.day(s.owner(), date);
    return new DayView(
        date,
        d == null ? 0 : d.revision(),
        d == null || d.activePlanId() == null ? null : plan(d.activePlanId()));
  }

  public DayView cancel(LocalDate date, PlannerController.Cancel v) {
    s.lock();
    require(v.confirmed(), "EXPLICIT_CONFIRMATION_REQUIRED", "必须明确确认取消计划");
    var before = day(date);
    s.conflict(before.revision() == v.revision() && before.activePlan() != null);
    db.daySet(s.owner(), date, null);
    audit.record(
        "daily_plan",
        before.activePlan().plan().id(),
        "CANCEL",
        before,
        Map.of("reason", v.reason(), "day", day(date)));
    return day(date);
  }

  public LearningModels.Page<Plan> history(LocalDate date, int page, int size) {
    s.page(page, size);
    return new LearningModels.Page<>(
        db.plans(s.owner(), date, size, page * size), db.planCount(s.owner(), date), page, size);
  }
}
