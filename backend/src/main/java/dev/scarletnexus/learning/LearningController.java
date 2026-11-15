package dev.scarletnexus.learning;

import dev.scarletnexus.learning.LearningModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/learning")
public class LearningController {
  private final LearningService service;

  public LearningController(LearningService service) {
    this.service = service;
  }

  public record GoalWrite(
      @NotNull UUID skillId,
      @NotBlank @Size(max = 160) String title,
      @NotNull @Size(max = 8000) String targetDescription,
      LocalDate deadline,
      @Min(1) @Max(5) int priority,
      @NotNull @Pattern(regexp = "ACTIVE|COMPLETED|ARCHIVED") String status,
      @Min(0) int revision) {}

  public record EntryWrite(
      @NotNull UUID nodeId, @Min(0) @Max(5) int targetLevel, boolean required) {}

  public record StageWrite(
      @NotBlank @Size(max = 160) String title,
      @NotEmpty @Size(max = 300) List<@NotNull @Valid EntryWrite> nodes) {}

  public record PathWrite(
      @NotNull UUID skillId,
      UUID goalId,
      UUID baseVersionId,
      @NotBlank @Size(max = 160) String title,
      @NotEmpty @Size(max = 30) List<@NotNull @Valid StageWrite> stages) {}

  public record Choose(@NotNull UUID pathId) {}

  public record EnrollmentState(
      @NotNull @Pattern(regexp = "ACTIVE|PAUSED|COMPLETED") String status) {}

  public record ProgressWrite(
      @NotNull @Pattern(regexp = "NOT_STARTED|IN_PROGRESS|COMPLETED") String status,
      @Min(0) int revision) {}

  public record TaskCreate(
      @NotNull UUID skillId,
      UUID goalId,
      @NotNull UUID nodeId,
      UUID enrollmentId,
      UUID entryId,
      @NotBlank @Size(max = 160) String title,
      @Min(1) @Max(1440) int plannedMinutes,
      LocalDate dueDate) {}

  public record TaskWrite(
      @NotBlank @Size(max = 160) String title,
      @Min(1) @Max(1440) int plannedMinutes,
      LocalDate dueDate,
      @NotNull @Pattern(regexp = "TODO|DOING|DONE|DEFERRED|CANCELLED") String status,
      @Min(0) int revision) {}

  public record SessionStart(@NotNull UUID taskId) {}

  public record SessionAction(
      @NotNull @Pattern(regexp = "PAUSE|RESUME|FINISH") String action, @Min(0) int revision) {}

  public record NoteWrite(@NotNull @Size(max = 20000) String body, @Min(0) int revision) {}

  public record ResourceWrite(
      @NotBlank @Size(max = 160) String title,
      @NotNull @Pattern(regexp = "WEB|VIDEO|BOOK|OTHER") String resourceType,
      @NotBlank @Size(max = 2048) String url,
      @NotNull @Pattern(regexp = "ACTIVE|ARCHIVED") String status,
      @Min(0) int revision) {}

  @GetMapping("/goals")
  public Page<Goal> goals(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return service.goals(page, size);
  }

  @PostMapping("/goals")
  public Goal createGoal(@Valid @RequestBody GoalWrite value) {
    return service.saveGoal(null, value);
  }

  @PatchMapping("/goals/{id}")
  public Goal goal(@PathVariable UUID id, @Valid @RequestBody GoalWrite value) {
    return service.saveGoal(id, value);
  }

  @GetMapping("/paths")
  public Page<PathVersion> paths(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return service.paths(page, size);
  }

  @GetMapping("/paths/{id}")
  public PathDetail path(@PathVariable UUID id) {
    return service.path(id);
  }

  @PostMapping("/paths")
  public PathDetail savePath(@Valid @RequestBody PathWrite value) {
    return service.savePath(value);
  }

  @GetMapping("/enrollments")
  public Page<Enrollment> enrollments(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return service.enrollments(page, size);
  }

  @PostMapping("/enrollments")
  public EnrollmentDetail choose(@Valid @RequestBody Choose value) {
    return service.choose(value.pathId());
  }

  @GetMapping("/enrollments/{id}")
  public EnrollmentDetail enrollment(@PathVariable UUID id) {
    return service.enrollmentDetail(id);
  }

  @PatchMapping("/enrollments/{id}")
  public EnrollmentDetail state(@PathVariable UUID id, @Valid @RequestBody EnrollmentState value) {
    return service.enrollmentState(id, value.status());
  }

  @PutMapping("/enrollments/{id}/progress/{entry}")
  public Progress progress(
      @PathVariable UUID id, @PathVariable UUID entry, @Valid @RequestBody ProgressWrite value) {
    return service.saveProgress(id, entry, value);
  }

  @GetMapping("/tasks")
  public Page<Task> tasks(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return service.tasks(page, size);
  }

  @PostMapping("/tasks")
  public Task task(@Valid @RequestBody TaskCreate value) {
    return service.createTask(value);
  }

  @GetMapping("/tasks/{id}")
  public Task task(@PathVariable UUID id) {
    return service.task(id);
  }

  @PatchMapping("/tasks/{id}")
  public Task task(@PathVariable UUID id, @Valid @RequestBody TaskWrite value) {
    return service.saveTask(id, value);
  }

  @GetMapping("/tasks/{id}/workbench")
  public LearningService.Workbench workbench(@PathVariable UUID id) {
    return service.workbench(id);
  }

  @GetMapping("/tasks/{id}/sessions")
  public Page<SessionView> sessions(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.sessions(id, page, size);
  }

  @PostMapping("/sessions")
  public SessionView start(
      @Valid @RequestBody SessionStart value, @RequestHeader("Idempotency-Key") String key) {
    return service.start(value.taskId(), key);
  }

  @PostMapping("/sessions/{id}/actions")
  public SessionView transition(
      @PathVariable UUID id,
      @Valid @RequestBody SessionAction value,
      @RequestHeader("Idempotency-Key") String key) {
    return service.transition(id, value, key);
  }

  @PutMapping("/tasks/{id}/note")
  public Note note(@PathVariable UUID id, @Valid @RequestBody NoteWrite value) {
    return service.saveNote(id, value);
  }

  @GetMapping("/nodes/{id}/resources")
  public Page<Resource> resources(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.resources(id, page, size);
  }

  @PostMapping("/nodes/{id}/resources")
  public Resource createResource(@PathVariable UUID id, @Valid @RequestBody ResourceWrite value) {
    return service.createResource(id, value);
  }

  @PatchMapping("/resources/{id}")
  public Resource resource(@PathVariable UUID id, @Valid @RequestBody ResourceWrite value) {
    return service.saveResource(id, value);
  }

  @GetMapping("/dashboard")
  public LearningService.Dashboard dashboard() {
    return service.dashboard();
  }
}
