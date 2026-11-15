package dev.scarletnexus.learning;

import static dev.scarletnexus.common.DomainFailure.require;

import dev.scarletnexus.common.*;
import dev.scarletnexus.identity.CurrentUser;
import dev.scarletnexus.learning.LearningController.*;
import dev.scarletnexus.learning.LearningModels.*;
import dev.scarletnexus.skill.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LearningService {
  private final LearningMapper db;
  private final CatalogMapper catalog;
  private final AuditLog audit;

  public LearningService(LearningMapper db, CatalogMapper catalog, AuditLog audit) {
    this.db = db;
    this.catalog = catalog;
    this.audit = audit;
  }

  private UUID owner() {
    return CurrentUser.id();
  }

  private void lock() {
    if (catalog.lockOwner(owner()) == null) throw DomainFailure.missing();
  }

  private <T> T found(T value) {
    if (value == null) throw DomainFailure.missing();
    return value;
  }

  private void conflict(boolean valid) {
    if (!valid)
      throw new DomainFailure(HttpStatus.CONFLICT, "LEARNING_CONFLICT", "记录或学习状态已变化，请刷新后重试");
  }

  private void page(int page, int size) {
    require(page >= 0 && page <= 100000 && size > 0 && size <= 100, "INVALID_QUERY", "分页参数无效");
  }

  private CatalogModels.Skill skill(UUID id, boolean active) {
    var s = found(catalog.skill(owner(), id));
    if (active) require(s.status().equals("ACTIVE"), "ARCHIVED_SKILL", "请先恢复技能");
    return s;
  }

  private CatalogModels.Node node(UUID id) {
    return found(catalog.node(owner(), id));
  }

  private void availableNode(UUID skill, UUID node) {
    require(node(node).status().equals("ACTIVE"), "ARCHIVED_NODE", "请先恢复节点");
    require(
        catalog.positions(owner(), skill).stream().anyMatch(p -> p.nodeId().equals(node)),
        "NODE_NOT_IN_SKILL",
        "节点不在所选技能中");
  }

  private Goal goal(UUID id) {
    return found(db.goal(owner(), id));
  }

  private void goalSkill(UUID goal, UUID skill) {
    if (goal != null)
      require(goal(goal).skillId().equals(skill), "GOAL_SKILL_MISMATCH", "目标与技能不匹配");
  }

  public Page<Goal> goals(int p, int s) {
    page(p, s);
    return new Page<>(db.goals(owner(), s, p * s), db.goalCount(owner()), p, s);
  }

  public Goal saveGoal(UUID id, GoalWrite v) {
    lock();
    Goal before = id == null ? null : goal(id);
    skill(v.skillId(), before == null);
    if (before != null) {
      conflict(before.revision() == v.revision());
      require(before.skillId().equals(v.skillId()), "IMMUTABLE_SKILL", "目标不能更换所属技能");
    }
    UUID key = id == null ? UUID.randomUUID() : id;
    if (before == null)
      db.insertGoal(
          owner(),
          key,
          v.skillId(),
          v.title().strip(),
          v.targetDescription(),
          v.deadline(),
          v.priority(),
          v.status());
    else
      db.updateGoal(
          owner(),
          key,
          v.title().strip(),
          v.targetDescription(),
          v.deadline(),
          v.priority(),
          v.status());
    var after = goal(key);
    audit.record("learning_goal", key, "SAVE", before, after);
    return after;
  }

  public Page<PathVersion> paths(int p, int s) {
    page(p, s);
    return new Page<>(db.paths(owner(), s, p * s), db.pathCount(owner()), p, s);
  }

  public PathDetail path(UUID id) {
    return new PathDetail(
        found(db.path(owner(), id)), db.stages(owner(), id), db.entries(owner(), id));
  }

  public PathDetail savePath(PathWrite v) {
    lock();
    skill(v.skillId(), true);
    goalSkill(v.goalId(), v.skillId());
    PathVersion base = v.baseVersionId() == null ? null : path(v.baseVersionId()).path();
    if (base != null) {
      require(base.skillId().equals(v.skillId()), "PATH_SKILL_MISMATCH", "路线不能更换所属技能");
      conflict(db.latestVersion(owner(), base.familyId()) == base.versionNumber());
    }
    int count = v.stages().stream().mapToInt(s -> s.nodes().size()).sum();
    require(count <= 300, "PATH_TOO_LARGE", "每版路线最多300个节点");
    Set<UUID> seen = new HashSet<>();
    for (var stage : v.stages())
      for (var e : stage.nodes()) {
        require(seen.add(e.nodeId()), "DUPLICATE_PATH_NODE", "路线节点不能重复");
        availableNode(v.skillId(), e.nodeId());
      }
    UUID id = UUID.randomUUID();
    db.insertPath(
        owner(),
        id,
        v.skillId(),
        v.goalId(),
        base == null ? id : base.familyId(),
        base == null ? 1 : base.versionNumber() + 1,
        base == null ? null : base.id(),
        v.title().strip());
    for (int i = 0; i < v.stages().size(); i++) {
      var stage = v.stages().get(i);
      UUID sid = UUID.randomUUID();
      db.insertStage(owner(), sid, id, stage.title().strip(), i);
      for (int j = 0; j < stage.nodes().size(); j++) {
        var e = stage.nodes().get(j);
        db.insertEntry(
            owner(),
            UUID.randomUUID(),
            id,
            sid,
            e.nodeId(),
            node(e.nodeId()).title(),
            j,
            e.targetLevel(),
            e.required());
      }
    }
    var after = path(id);
    audit.record("learning_path", id, "VERSION", base, after);
    return after;
  }

  public Page<Enrollment> enrollments(int p, int s) {
    page(p, s);
    return new Page<>(db.enrollments(owner(), s, p * s), db.enrollmentCount(owner()), p, s);
  }

  private Enrollment enrollment(UUID id) {
    return found(db.enrollment(owner(), id));
  }

  public EnrollmentDetail enrollmentDetail(UUID id) {
    var en = enrollment(id);
    var route = path(en.pathId());
    var progress = db.progress(owner(), id);
    Set<UUID> done = new HashSet<>();
    progress.stream()
        .filter(p -> p.status().equals("COMPLETED"))
        .forEach(p -> done.add(p.entryId()));
    long total = route.entries().stream().filter(Entry::required).count(),
        completed =
            route.entries().stream()
                .filter(Entry::required)
                .filter(e -> done.contains(e.id()))
                .count();
    return new EnrollmentDetail(en, route, progress, completed, total);
  }

  public EnrollmentDetail choose(UUID pathId) {
    lock();
    var route = path(pathId);
    var old = db.enrolled(owner(), pathId);
    if (old == null) skill(route.path().skillId(), true);
    db.pauseEnrollments(owner());
    UUID id = old == null ? UUID.randomUUID() : old.id();
    if (old == null) db.insertEnrollment(owner(), id, pathId);
    else db.enrollmentState(owner(), id, "ACTIVE");
    var after = enrollment(id);
    audit.record("path_enrollment", id, "SELECT", old, after);
    return enrollmentDetail(id);
  }

  public EnrollmentDetail enrollmentState(UUID id, String state) {
    lock();
    var before = enrollment(id);
    if (state.equals("ACTIVE")) db.pauseEnrollments(owner());
    if (state.equals("COMPLETED")) {
      var d = enrollmentDetail(id);
      require(d.completedRequired() == d.totalRequired(), "REQUIRED_NODES_INCOMPLETE", "请先完成必修节点");
    }
    db.enrollmentState(owner(), id, state);
    audit.record("path_enrollment", id, "STATE", before, enrollment(id));
    return enrollmentDetail(id);
  }

  public Progress saveProgress(UUID enrollment, UUID entry, ProgressWrite v) {
    lock();
    var en = enrollment(enrollment);
    require(!en.status().equals("COMPLETED"), "ENROLLMENT_COMPLETED", "请先重新选择此路线，再修改学习进度");
    var e = found(db.entry(owner(), entry));
    require(e.pathId().equals(en.pathId()), "ENTRY_PATH_MISMATCH", "节点不属于此路线版本");
    var before = db.progressOne(owner(), enrollment, entry);
    conflict(before == null ? v.revision() == 0 : before.revision() == v.revision());
    db.saveProgress(owner(), UUID.randomUUID(), enrollment, en.pathId(), entry, v.status());
    var after = db.progressOne(owner(), enrollment, entry);
    audit.record("learning_progress", entry, "SELF_REPORT", before, after);
    return after;
  }

  public Page<Task> tasks(int p, int s) {
    page(p, s);
    return new Page<>(db.tasks(owner(), s, p * s), db.taskCount(owner()), p, s);
  }

  public Task task(UUID id) {
    return found(db.task(owner(), id));
  }

  public Task createTask(TaskCreate v) {
    lock();
    skill(v.skillId(), true);
    availableNode(v.skillId(), v.nodeId());
    goalSkill(v.goalId(), v.skillId());
    UUID path = null;
    require(
        (v.enrollmentId() == null) == (v.entryId() == null),
        "INCOMPLETE_ROUTE_REFERENCE",
        "请同时选择路线和路线节点");
    if (v.enrollmentId() != null) {
      var en = enrollment(v.enrollmentId());
      var e = found(db.entry(owner(), v.entryId()));
      var pv = path(en.pathId()).path();
      require(
          e.pathId().equals(en.pathId())
              && e.nodeId().equals(v.nodeId())
              && pv.skillId().equals(v.skillId())
              && Objects.equals(pv.goalId(), v.goalId()),
          "TASK_ROUTE_MISMATCH",
          "任务的路线、目标和节点不匹配");
      path = en.pathId();
    }
    UUID id = UUID.randomUUID();
    db.insertTask(
        owner(),
        id,
        v.skillId(),
        v.goalId(),
        v.nodeId(),
        v.enrollmentId(),
        v.entryId(),
        path,
        v.title().strip(),
        v.plannedMinutes(),
        v.dueDate());
    var after = task(id);
    audit.record("learning_task", id, "CREATE", null, after);
    return after;
  }

  public Task saveTask(UUID id, TaskWrite v) {
    lock();
    var before = task(id);
    conflict(before.revision() == v.revision());
    db.updateTask(owner(), id, v.title().strip(), v.plannedMinutes(), v.dueDate(), v.status());
    var after = task(id);
    audit.record("learning_task", id, "SAVE", before, after);
    return after;
  }

  private Session session(UUID id) {
    return found(db.session(owner(), id));
  }

  public static long elapsedMillis(Session s, Instant now) {
    return s.accumulatedMillis()
        + (s.state().equals("RUNNING")
            ? Math.max(0, Duration.between(s.segmentStartedAt(), now).toMillis())
            : 0);
  }

  private SessionView view(Session s) {
    if (s == null) return null;
    Instant now = Instant.now();
    return new SessionView(s, elapsedMillis(s, now) / 1000, now);
  }

  private void key(String key) {
    require(
        key != null && key.matches("[A-Za-z0-9_-]{1,100}"), "INVALID_IDEMPOTENCY_KEY", "操作键格式无效");
  }

  public SessionView start(UUID taskId, String key) {
    lock();
    key(key);
    var task = task(taskId);
    var replay = db.command(owner(), key);
    if (replay != null) {
      var existing = session(replay.sessionId());
      conflict(replay.action().equals("START") && existing.taskId().equals(taskId));
      return view(existing);
    }
    require(
        !Set.of("DONE", "CANCELLED").contains(task.status()),
        "TASK_NOT_LEARNABLE",
        "已完成或取消的任务须先恢复状态");
    conflict(db.openSession(owner()) == null);
    UUID id = UUID.randomUUID();
    db.insertSession(owner(), id, taskId, Instant.now());
    db.commandInsert(owner(), UUID.randomUUID(), key, id, "START", 0);
    if (!task.status().equals("DOING")) {
      db.updateTask(owner(), taskId, task.title(), task.plannedMinutes(), task.dueDate(), "DOING");
      audit.record("learning_task", taskId, "START", task, task(taskId));
    }
    var after = session(id);
    audit.record("learning_session", id, "START", null, after);
    return view(after);
  }

  public SessionView transition(UUID id, SessionAction v, String key) {
    lock();
    key(key);
    var before = session(id);
    var replay = db.command(owner(), key);
    if (replay != null) {
      conflict(
          replay.sessionId().equals(id)
              && replay.action().equals(v.action())
              && replay.requestRevision() == v.revision());
      return view(before);
    }
    conflict(before.revision() == v.revision() && !before.state().equals("ENDED"));
    String next =
        switch (v.action()) {
          case "PAUSE" -> {
            conflict(before.state().equals("RUNNING"));
            yield "PAUSED";
          }
          case "RESUME" -> {
            conflict(before.state().equals("PAUSED"));
            yield "RUNNING";
          }
          case "FINISH" -> "ENDED";
          default -> throw new IllegalArgumentException("Invalid session action");
        };
    Instant now = Instant.now();
    db.updateSession(
        owner(),
        id,
        next,
        next.equals("RUNNING") ? now : null,
        next.equals("ENDED") ? now : null,
        elapsedMillis(before, now));
    db.commandInsert(owner(), UUID.randomUUID(), key, id, v.action(), v.revision());
    var after = session(id);
    audit.record("learning_session", id, v.action(), before, after);
    return view(after);
  }

  public Page<SessionView> sessions(UUID task, int p, int s) {
    task(task);
    page(p, s);
    return new Page<>(
        db.sessions(owner(), task, s, p * s).stream().map(this::view).toList(),
        db.sessionCount(owner(), task),
        p,
        s);
  }

  public Note saveNote(UUID task, NoteWrite v) {
    lock();
    task(task);
    var before = db.note(owner(), task);
    conflict(before == null ? v.revision() == 0 : before.revision() == v.revision());
    db.saveNote(owner(), UUID.randomUUID(), task, v.body());
    var after = db.note(owner(), task);
    audit.record("learning_note", after.id(), "SAVE", before, after);
    return after;
  }

  private void url(String value) {
    boolean valid = false;
    try {
      var uri = URI.create(value);
      valid =
          ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
              && uri.getHost() != null
              && uri.getUserInfo() == null;
    } catch (IllegalArgumentException ignored) {
    }
    require(valid, "INVALID_RESOURCE_URL", "资料地址须为不含账号密码的http(s)链接");
  }

  public Page<Resource> resources(UUID node, int p, int s) {
    node(node);
    page(p, s);
    return new Page<>(db.resources(owner(), node, s, p * s), db.resourceCount(owner(), node), p, s);
  }

  public Resource createResource(UUID node, ResourceWrite v) {
    lock();
    node(node);
    url(v.url());
    require(v.status().equals("ACTIVE"), "INVALID_RESOURCE_STATUS", "新资料须为使用中");
    UUID id = UUID.randomUUID();
    db.insertResource(owner(), id, v.title().strip(), v.resourceType(), v.url());
    db.attachResource(owner(), UUID.randomUUID(), node, id);
    var after = found(db.resource(owner(), id));
    audit.record("learning_resource", id, "CREATE", null, after);
    return after;
  }

  public Resource saveResource(UUID id, ResourceWrite v) {
    lock();
    var before = found(db.resource(owner(), id));
    conflict(before.revision() == v.revision());
    url(v.url());
    db.updateResource(owner(), id, v.title().strip(), v.resourceType(), v.url(), v.status());
    var after = found(db.resource(owner(), id));
    audit.record("learning_resource", id, "SAVE", before, after);
    return after;
  }

  public record Workbench(
      Task task,
      CatalogModels.Node node,
      EnrollmentDetail route,
      List<CatalogModels.Node> prerequisites,
      Note note,
      SessionView openSession) {}

  public Workbench workbench(UUID id) {
    var t = task(id);
    var n = node(t.nodeId());
    var pre =
        catalog.relations(owner()).stream()
            .filter(
                r -> r.relationType().equals("PREREQUISITE_OF") && r.targetNodeId().equals(n.id()))
            .map(r -> node(r.sourceNodeId()))
            .toList();
    return new Workbench(
        t,
        n,
        t.enrollmentId() == null ? null : enrollmentDetail(t.enrollmentId()),
        pre,
        db.note(owner(), id),
        view(db.openSession(owner())));
  }

  public record Dashboard(
      SessionView openSession,
      EnrollmentDetail activeEnrollment,
      Task continuationTask,
      Page<Task> tasks) {}

  public Dashboard dashboard() {
    var en = db.activeEnrollment(owner());
    return new Dashboard(
        view(db.openSession(owner())),
        en == null ? null : enrollmentDetail(en.id()),
        db.continuationTask(owner()),
        tasks(0, 10));
  }
}
