package dev.scarletnexus.planner;

import dev.scarletnexus.learning.LearningModels.Page;
import dev.scarletnexus.planner.PlannerModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/planner")
public class PlannerController {
  private final PlannerService service;

  public PlannerController(PlannerService service) {
    this.service = service;
  }

  public record Create(
      @NotNull LocalDate planDate,
      @NotBlank @Size(max = 80) String timeZone,
      @Min(0) @Max(1440) int budgetMinutes,
      @NotEmpty @Size(max = 100) List<@NotNull SkillLimit> skillLimits) {}

  public record Choose(
      @Min(0) int revision, @NotNull @Size(max = 200) List<@NotNull UUID> itemIds) {}

  public record Confirm(@Min(0) int revision, @Min(0) int dayRevision, boolean confirmed) {}

  public record Cancel(
      @Min(0) int revision, boolean confirmed, @NotBlank @Size(max = 2000) String reason) {}

  @PostMapping("/drafts")
  public DraftView create(@Valid @RequestBody Create v) {
    return service.create(v);
  }

  @GetMapping("/drafts")
  public Page<Draft> drafts(
      @RequestParam LocalDate date,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.drafts(date, page, size);
  }

  @GetMapping("/drafts/{id}")
  public DraftView draft(@PathVariable UUID id) {
    return service.view(id);
  }

  @PatchMapping("/drafts/{id}")
  public DraftView choose(@PathVariable UUID id, @Valid @RequestBody Choose v) {
    return service.choose(id, v);
  }

  @PostMapping("/drafts/{id}/confirm")
  public PlanView confirm(
      @PathVariable UUID id,
      @Valid @RequestBody Confirm v,
      @RequestHeader("Idempotency-Key") String key) {
    return service.confirm(id, v, key);
  }

  @GetMapping("/plans/{id}")
  public PlanView plan(@PathVariable UUID id) {
    return service.plan(id);
  }

  @GetMapping("/days/{date}")
  public DayView day(@PathVariable LocalDate date) {
    return service.day(date);
  }

  @PostMapping("/days/{date}/cancel")
  public DayView cancel(@PathVariable LocalDate date, @Valid @RequestBody Cancel v) {
    return service.cancel(date, v);
  }

  @GetMapping("/days/{date}/history")
  public Page<Plan> history(
      @PathVariable LocalDate date,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.history(date, page, size);
  }
}
