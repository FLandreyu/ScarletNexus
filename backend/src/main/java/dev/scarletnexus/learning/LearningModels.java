package dev.scarletnexus.learning;

import java.time.*;
import java.util.*;

public final class LearningModels {
  private LearningModels() {}

  public record Goal(
      UUID id,
      UUID skillId,
      String title,
      String targetDescription,
      LocalDate deadline,
      int priority,
      String status,
      int revision) {}

  public record PathVersion(
      UUID id,
      UUID skillId,
      UUID goalId,
      UUID familyId,
      int versionNumber,
      UUID previousVersionId,
      String title,
      Instant createdAt) {}

  public record Stage(UUID id, UUID pathId, String title, int displayOrder) {}

  public record Entry(
      UUID id,
      UUID pathId,
      UUID stageId,
      UUID nodeId,
      String titleSnapshot,
      int displayOrder,
      int targetLevel,
      boolean required) {}

  public record PathDetail(PathVersion path, List<Stage> stages, List<Entry> entries) {}

  public record Enrollment(UUID id, UUID pathId, String status, Instant startedAt) {}

  public record Progress(UUID entryId, String status, int revision) {}

  public record EnrollmentDetail(
      Enrollment enrollment,
      PathDetail route,
      List<Progress> progress,
      long completedRequired,
      long totalRequired) {}

  public record Task(
      UUID id,
      UUID skillId,
      UUID goalId,
      UUID nodeId,
      UUID enrollmentId,
      UUID entryId,
      UUID pathId,
      String title,
      int plannedMinutes,
      LocalDate dueDate,
      String status,
      int revision) {}

  public record Session(
      UUID id,
      UUID taskId,
      String state,
      Instant startedAt,
      Instant segmentStartedAt,
      Instant endedAt,
      long accumulatedMillis,
      int revision) {}

  public record SessionView(Session session, long elapsedSeconds, Instant serverNow) {}

  public record Command(UUID sessionId, String action, int requestRevision) {}

  public record Note(UUID id, UUID taskId, String body, int revision, Instant updatedAt) {}

  public record Resource(
      UUID id, String title, String resourceType, String url, String status, int revision) {}

  public record Page<T>(List<T> items, long total, int page, int size) {}
}
