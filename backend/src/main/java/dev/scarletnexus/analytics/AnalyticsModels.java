package dev.scarletnexus.analytics;

import java.time.*;
import java.util.*;

public final class AnalyticsModels {
  private AnalyticsModels() {}

  public record Tasks(UUID skillId, long total, long done, long cancelled) {}

  public record Time(UUID skillId, LocalDate day, long milliseconds) {}

  public record Attempts(
      UUID skillId,
      LocalDate day,
      long total,
      long independent,
      long judged,
      long correct,
      long unsure) {}

  public record Totals(
      long studyMilliseconds,
      long tasks,
      long doneTasks,
      long cancelledTasks,
      long taskDenominator,
      long attempts,
      long independentAttempts,
      long judgedIndependentAttempts,
      long correctIndependentAttempts,
      long unsureOrUnreviewedAttempts) {}

  public record Skill(UUID id, String name, String status, Totals totals) {}

  public record Day(
      LocalDate date,
      long studyMilliseconds,
      long attempts,
      long independentAttempts,
      long judgedIndependentAttempts,
      long correctIndependentAttempts) {}

  public record Route(
      UUID enrollmentId, UUID pathId, String title, long completedRequired, long totalRequired) {}

  public record Report(
      LocalDate from,
      LocalDate to,
      String timeZone,
      UUID skillId,
      Instant generatedAt,
      Totals totals,
      List<Skill> skills,
      List<Day> days,
      List<Route> routes,
      List<String> definitions) {}

  public record Assessment(
      UUID id,
      UUID nodeId,
      String title,
      int level,
      String confidence,
      String assessorType,
      String reason,
      Instant assessedAt) {}

  public record AssessmentView(Assessment assessment, List<UUID> evidenceIds) {}
}
