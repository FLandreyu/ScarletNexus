package dev.scarletnexus.planner;

import dev.scarletnexus.learning.LearningModels.Task;
import java.time.*;
import java.util.*;

public final class PlannerModels {
  private PlannerModels() {}

  public record SkillLimit(UUID skillId, int maxMinutes) {}

  public record TaskRow(
      UUID id,
      UUID skillId,
      UUID goalId,
      UUID nodeId,
      String title,
      int plannedMinutes,
      LocalDate dueDate,
      String status,
      int revision,
      String skillStatus,
      String nodeStatus,
      String goalStatus,
      int priority,
      LocalDate goalDeadline,
      String latestOutcome,
      Instant createdAt) {}

  public record Schedule(
      UUID id,
      UUID skillId,
      UUID nodeId,
      UUID originalTaskId,
      UUID initialEvidenceId,
      int plannedMinutes,
      String timeZone,
      Instant nextReviewAt,
      int intervalDays,
      int cycleNumber,
      int revision,
      String status,
      String reason,
      Instant createdAt,
      Instant lastCompletedAt) {}

  public record ReviewRow(
      UUID id,
      UUID skillId,
      UUID nodeId,
      String title,
      int plannedMinutes,
      Instant nextReviewAt,
      int revision,
      int cycleNumber,
      String status,
      String skillStatus,
      String nodeStatus,
      boolean attached,
      UUID taskId,
      Integer taskRevision,
      String taskStatus,
      Instant createdAt) {}

  public record Candidate(
      String kind,
      UUID sourceId,
      UUID taskId,
      UUID reviewId,
      UUID skillId,
      UUID nodeId,
      String title,
      int minutes,
      Integer taskRevision,
      Integer reviewRevision,
      boolean eligible,
      String reason,
      int priority,
      LocalDate deadline,
      boolean continuing,
      int weakness,
      Instant orderTime) {
    public String key() {
      return kind + ":" + sourceId;
    }
  }

  public record Decision(Candidate candidate, boolean selected, String reason, int order) {}

  public record Draft(
      UUID id,
      LocalDate planDate,
      String timeZone,
      int budgetMinutes,
      String sourceHash,
      int revision,
      String state,
      Instant createdAt) {}

  public record DraftItem(
      UUID id,
      String kind,
      UUID sourceId,
      UUID taskId,
      UUID reviewId,
      UUID skillId,
      UUID nodeId,
      String title,
      int minutes,
      Integer taskRevision,
      Integer reviewRevision,
      boolean eligible,
      boolean selected,
      int displayOrder,
      String reason) {
    public String key() {
      return kind + ":" + sourceId;
    }
  }

  public record DraftView(
      Draft draft,
      List<SkillLimit> skillLimits,
      List<DraftItem> items,
      int selectedMinutes,
      boolean sourceChanged) {}

  public record Plan(
      UUID id,
      UUID draftId,
      LocalDate planDate,
      String timeZone,
      int versionNumber,
      int budgetMinutes,
      Instant confirmedAt) {}

  public record PlanItem(
      UUID id,
      UUID taskId,
      UUID reviewId,
      UUID skillId,
      String titleSnapshot,
      int plannedMinutes,
      int taskRevision,
      int displayOrder,
      String reason) {}

  public record PlanItemView(PlanItem item, Task current, boolean changed) {}

  public record PlanView(
      Plan plan, List<SkillLimit> skillLimits, List<PlanItemView> items, int plannedMinutes) {}

  public record Day(UUID activePlanId, int revision) {}

  public record DayView(LocalDate planDate, int revision, PlanView activePlan) {}

  public record Key(UUID planId, String inputHash) {}

  public record ReviewEvent(
      UUID id,
      UUID scheduleId,
      String kind,
      String requestKey,
      String inputHash,
      UUID evidenceId,
      UUID attemptId,
      String outcome,
      String verification,
      Boolean independent,
      Instant previousDue,
      Instant nextDue,
      Integer previousInterval,
      int nextInterval,
      String reason,
      Instant createdAt) {}

  public record ReviewView(Schedule schedule, String title, UUID cycleTaskId) {}

  public record Adjustment(
      UUID id,
      UUID taskId,
      String kind,
      String inputHash,
      LocalDate previousDue,
      LocalDate nextDue,
      String previousStatus,
      String nextStatus,
      int previousRevision,
      int nextRevision,
      String reason,
      Instant createdAt) {}

  public record AdjustmentView(Adjustment adjustment, List<UUID> childTaskIds) {}
}
