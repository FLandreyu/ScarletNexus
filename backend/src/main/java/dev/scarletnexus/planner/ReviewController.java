package dev.scarletnexus.planner;

import dev.scarletnexus.learning.LearningModels.Page;
import dev.scarletnexus.planner.PlannerModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {
  private final ReviewService service;

  public ReviewController(ReviewService service) {
    this.service = service;
  }

  public record Create(
      @NotNull UUID taskId,
      UUID evidenceId,
      LocalDate firstDate,
      @NotBlank @Size(max = 80) String timeZone,
      @Min(1) @Max(1440) int plannedMinutes,
      @NotBlank @Size(max = 2000) String reason) {}

  public record Adjust(
      @Min(0) int revision,
      @NotNull LocalDate nextDate,
      @NotNull @Pattern(regexp = "ACTIVE|PAUSED") String status,
      @NotBlank @Size(max = 2000) String reason) {}

  public record Complete(
      @Min(0) int revision, @NotNull UUID evidenceId, @NotBlank @Size(max = 2000) String reason) {}

  @GetMapping("/schedules")
  public Page<ReviewView> list(
      @RequestParam(defaultValue = "false") boolean due,
      @RequestParam(required = false) Instant until,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.list(due, until == null ? Instant.now() : until, page, size);
  }

  @PostMapping("/schedules")
  public ReviewView create(@Valid @RequestBody Create v) {
    return service.create(v);
  }

  @GetMapping("/schedules/{id}")
  public ReviewView get(@PathVariable UUID id) {
    return service.view(id);
  }

  @PatchMapping("/schedules/{id}")
  public ReviewView adjust(@PathVariable UUID id, @Valid @RequestBody Adjust v) {
    return service.adjust(id, v);
  }

  @PostMapping("/schedules/{id}/complete")
  public ReviewView complete(
      @PathVariable UUID id,
      @Valid @RequestBody Complete v,
      @RequestHeader("Idempotency-Key") String key) {
    return service.complete(id, v, key);
  }

  @GetMapping("/schedules/{id}/history")
  public Page<ReviewEvent> history(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.history(id, page, size);
  }
}
