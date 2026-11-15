package dev.scarletnexus.planner;

import static dev.scarletnexus.common.DomainFailure.require;

import dev.scarletnexus.common.AuditLog;
import dev.scarletnexus.learning.*;
import dev.scarletnexus.planner.PlannerModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/planner/tasks")
@Transactional
public class AdjustmentController {
  private final AdjustmentMapper db;
  private final LearningMapper tasks;
  private final LearningService learning;
  private final PlanningSupport s;
  private final AuditLog audit;

  public AdjustmentController(
      AdjustmentMapper db,
      LearningMapper tasks,
      LearningService learning,
      PlanningSupport s,
      AuditLog audit) {
    this.db = db;
    this.tasks = tasks;
    this.learning = learning;
    this.s = s;
    this.audit = audit;
  }

  public record Part(
      @NotBlank @Size(max = 160) String title, @Min(1) @Max(1440) int minutes, LocalDate dueDate) {}

  public record Write(
      @NotNull @Pattern(regexp = "DEFER|CANCEL|SPLIT") String kind,
      @Min(0) int revision,
      LocalDate dueDate,
      @NotBlank @Size(max = 2000) String reason,
      @NotNull @Size(max = 8) List<@NotNull @Valid Part> parts) {}

  private AdjustmentView view(Adjustment a) {
    return new AdjustmentView(a, db.children(s.owner(), a.id()));
  }

  @PostMapping("/{id}/adjustments")
  public AdjustmentView adjust(
      @PathVariable UUID id,
      @Valid @RequestBody Write v,
      @RequestHeader("Idempotency-Key") String key) {
    s.lock();
    s.key(key);
    String hash = s.hash(List.of(id, v));
    var replay = db.replay(s.owner(), key);
    if (replay != null) {
      s.conflict(replay.inputHash().equals(hash));
      return view(replay);
    }
    var t = learning.task(id);
    s.conflict(t.revision() == v.revision());
    require(
        !Set.of("DONE", "CANCELLED").contains(t.status()), "TASK_ALREADY_ENDED", "已结束任务不能延期、拆分或取消");
    s.idle(id, tasks);
    boolean split = v.kind().equals("SPLIT");
    require(
        !split || !db.reviewTask(s.owner(), id), "REVIEW_SPLIT_UNSUPPORTED", "复习周期任务不能拆分，请调整复习日期");
    require(
        split ? v.parts().size() >= 2 : v.parts().isEmpty(),
        "INVALID_TASK_PARTS",
        "拆分须2至8项，其他操作不能包含子任务");
    if (split)
      require(
          v.parts().stream().mapToInt(Part::minutes).sum() == t.plannedMinutes(),
          "SPLIT_MINUTES_MISMATCH",
          "子任务预计分钟之和须等于原任务");
    if (v.kind().equals("DEFER"))
      require(
          v.dueDate() != null && (t.dueDate() == null || v.dueDate().isAfter(t.dueDate())),
          "INVALID_DEFER_DATE",
          "延期日期须晚于原截止日期");
    String status = v.kind().equals("DEFER") ? "DEFERRED" : "CANCELLED";
    LocalDate due = v.kind().equals("DEFER") ? v.dueDate() : t.dueDate();
    tasks.updateTask(s.owner(), id, t.title(), t.plannedMinutes(), due, status);
    UUID aid = UUID.randomUUID();
    db.insert(
        s.owner(),
        aid,
        id,
        v.kind(),
        key,
        hash,
        t.dueDate(),
        due,
        t.status(),
        status,
        t.revision(),
        t.revision() + 1,
        v.reason().strip());
    if (split)
      for (var part : v.parts()) {
        UUID child = UUID.randomUUID();
        tasks.insertTask(
            s.owner(),
            child,
            t.skillId(),
            t.goalId(),
            t.nodeId(),
            t.enrollmentId(),
            t.entryId(),
            t.pathId(),
            part.title().strip(),
            part.minutes(),
            part.dueDate());
        db.split(s.owner(), aid, id, child);
        audit.record("learning_task", child, "SPLIT_CREATE", null, learning.task(child));
      }
    audit.record(
        "learning_task", id, v.kind(), t, Map.of("task", learning.task(id), "reason", v.reason()));
    return view(s.found(db.get(s.owner(), aid)));
  }

  @GetMapping("/{id}/adjustments")
  public LearningModels.Page<AdjustmentView> history(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    learning.task(id);
    s.page(page, size);
    return new LearningModels.Page<>(
        db.history(s.owner(), id, size, page * size).stream().map(this::view).toList(),
        db.count(s.owner(), id),
        page,
        size);
  }
}
