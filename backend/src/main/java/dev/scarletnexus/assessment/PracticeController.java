package dev.scarletnexus.assessment;

import dev.scarletnexus.assessment.PracticeModels.*;
import dev.scarletnexus.learning.LearningModels.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/practice")
public class PracticeController {
  private final PracticeService service;

  public PracticeController(PracticeService service) {
    this.service = service;
  }

  public record QuestionWrite(
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 20000) String prompt,
      @NotNull @Pattern(regexp = "EXACT_TEXT|PROOF|PROJECT") String questionType,
      @NotNull @Size(max = 20000) String answerKey,
      @NotNull @Size(max = 8000) String hintText) {}

  public record State(@NotNull @Pattern(regexp = "ACTIVE|ARCHIVED") String status) {}

  public record Submit(
      @NotNull UUID questionId,
      @NotNull @Size(max = 20000) String content,
      boolean declaredIndependent,
      @NotNull @Size(max = 5) List<@NotNull UUID> attachmentIds) {}

  public record Hint(
      @NotNull @Pattern(regexp = "ONE_HINT|FULL_SOLUTION") String level, boolean confirmed) {}

  public record ReviewWrite(
      @NotNull @Pattern(regexp = "CORRECT|INCORRECT|UNSURE") String verdict,
      @NotBlank @Size(min = 5, max = 8000) String feedback,
      UUID tutorTurnId,
      boolean confirmed) {}

  public record Confirm(boolean confirmed) {}

  public record Assess(
      @NotNull @Pattern(regexp = "SELF|RULE") String assessorType,
      @Min(0) @Max(5) int level,
      @NotBlank @Size(max = 8000) String reason,
      @NotEmpty @Size(max = 100) List<@NotNull UUID> evidenceIds) {}

  @GetMapping("/tasks/{task}/questions")
  public Page<Question> questions(
      @PathVariable UUID task,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.questions(task, page, size);
  }

  @PostMapping("/tasks/{task}/questions")
  public Question question(@PathVariable UUID task, @Valid @RequestBody QuestionWrite v) {
    return service.createQuestion(task, v);
  }

  @PatchMapping("/questions/{id}")
  public Question state(@PathVariable UUID id, @Valid @RequestBody State v) {
    return service.questionState(id, v.status());
  }

  @GetMapping("/tasks/{task}/attempts")
  public Page<Attempt> attempts(
      @PathVariable UUID task,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.attempts(task, page, size);
  }

  @PostMapping("/tasks/{task}/attempts")
  public Attempt submit(
      @PathVariable UUID task,
      @Valid @RequestBody Submit v,
      @RequestHeader("Idempotency-Key") String key) {
    return service.submit(task, v, key);
  }

  @GetMapping("/attempts/{id}")
  public PracticeService.AttemptDetail attempt(@PathVariable UUID id) {
    return service.attemptDetail(id);
  }

  @PostMapping("/attempts/{id}/hint")
  public Map<String, Object> hint(@PathVariable UUID id, @Valid @RequestBody Hint v) {
    return service.hint(id, v);
  }

  @PostMapping("/attempts/{id}/check")
  public Review check(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key) {
    return service.check(id, key);
  }

  @PostMapping("/attempts/{id}/reviews")
  public Review review(
      @PathVariable UUID id,
      @Valid @RequestBody ReviewWrite v,
      @RequestHeader("Idempotency-Key") String key) {
    return service.review(id, v, key);
  }

  @GetMapping("/attempts/{id}/reviews")
  public Page<Review> reviews(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.reviews(id, page, size);
  }

  @PostMapping("/reviews/{id}/evidence")
  public Evidence evidence(@PathVariable UUID id, @Valid @RequestBody Confirm v) {
    return service.confirmEvidence(id, v.confirmed());
  }

  @GetMapping("/tasks/{task}/evidence")
  public Page<Evidence> evidence(
      @PathVariable UUID task,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.evidence(task, page, size);
  }

  @PostMapping("/tasks/{task}/assessments")
  public AssessmentView assess(@PathVariable UUID task, @Valid @RequestBody Assess v) {
    return service.assess(task, v);
  }

  @GetMapping("/tasks/{task}/assessments")
  public Page<AssessmentView> assessments(
      @PathVariable UUID task,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.assessments(task, page, size);
  }
}
