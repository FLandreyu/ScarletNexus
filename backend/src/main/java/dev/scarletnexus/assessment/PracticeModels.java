package dev.scarletnexus.assessment;

import java.time.Instant;
import java.util.*;

public final class PracticeModels {
  private PracticeModels() {}

  public record Question(
      UUID id, UUID nodeId, String title, String prompt, String questionType, String status) {}

  public record Secret(
      UUID id,
      UUID nodeId,
      String title,
      String prompt,
      String questionType,
      String status,
      String answerKey,
      String hintText) {
    public Question view() {
      return new Question(id, nodeId, title, prompt, questionType, status);
    }

    public String toString() {
      return "QuestionSecret[id=" + id + "]";
    }
  }

  public record Attempt(
      UUID id,
      UUID taskId,
      UUID nodeId,
      UUID questionId,
      String submittedContent,
      boolean independent,
      int hintLevelAtSubmission,
      Instant createdAt) {}

  public record AttemptKey(UUID id, String inputHash) {}

  public record Review(
      UUID id,
      UUID attemptId,
      UUID tutorTurnId,
      String verdict,
      String reviewerType,
      String feedback,
      Instant createdAt) {}

  public record ReviewKey(UUID id, String inputHash) {}

  public record Evidence(
      UUID id,
      UUID nodeId,
      UUID attemptId,
      UUID reviewId,
      String evidenceType,
      String verification,
      String outcome,
      boolean independent,
      Instant observedAt) {}

  public record Assessment(
      UUID id,
      UUID nodeId,
      int level,
      String confidence,
      String assessorType,
      String reason,
      Instant assessedAt) {}

  public record AssessmentView(Assessment assessment, List<UUID> evidenceIds) {}

  public record Attachment(
      UUID id,
      UUID taskId,
      long sizeBytes,
      int width,
      int height,
      String status,
      Instant createdAt) {}

  public record StoredAttachment(
      UUID id,
      UUID taskId,
      String storageKey,
      String checksum,
      long sizeBytes,
      int width,
      int height,
      String status) {}
}
