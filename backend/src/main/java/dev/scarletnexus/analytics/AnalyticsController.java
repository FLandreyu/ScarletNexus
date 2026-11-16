package dev.scarletnexus.analytics;

import dev.scarletnexus.analytics.AnalyticsModels.*;
import dev.scarletnexus.learning.LearningModels.Page;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
  private final AnalyticsService service;

  @GetMapping("/evidence/{id}")
  public AnalyticsService.EvidenceTrace evidence(@PathVariable UUID id) {
    return service.evidence(id);
  }

  public AnalyticsController(AnalyticsService service) {
    this.service = service;
  }

  @GetMapping
  public Report report(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "UTC") String timeZone,
      @RequestParam(required = false) UUID skillId) {
    return service.report(from, to, timeZone, skillId);
  }

  @GetMapping("/assessments")
  public Page<AssessmentView> assessments(
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "UTC") String timeZone,
      @RequestParam(required = false) UUID skillId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.assessments(to, timeZone, skillId, page, size);
  }
}
