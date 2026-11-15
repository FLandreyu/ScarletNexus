package dev.scarletnexus.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.scarletnexus.learning.LearningModels.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/tutor")
public class TutorController {
  private final TutorService service;
  private final ObjectMapper json;

  public TutorController(TutorService service, ObjectMapper json) {
    this.service = service;
    this.json = json;
  }

  public record Create(UUID questionId, @NotBlank @Size(max = 160) String title) {}

  public record Chat(
      @NotBlank @Size(max = 8000) String text,
      @NotNull @Pattern(regexp = "EXPLAIN|SOCRATIC|FULL_SOLUTION") String mode,
      UUID attemptId,
      boolean confirmed) {}

  public record Confirm(boolean confirmed) {}

  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of("enabled", service.enabled(), "imageAnalysisSupported", false);
  }

  @GetMapping("/tasks/{id}/conversations")
  public Page<TutorMapper.Conversation> conversations(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.conversations(id, page, size);
  }

  @PostMapping("/tasks/{id}/conversations")
  public TutorMapper.Conversation create(@PathVariable UUID id, @Valid @RequestBody Create v) {
    return service.create(id, v.questionId(), v.title());
  }

  @GetMapping("/conversations/{id}/turns")
  public Page<TutorMapper.Turn> turns(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.turns(id, page, size);
  }

  @GetMapping("/turns/{id}")
  public TutorMapper.Turn turn(@PathVariable UUID id) {
    return service.turn(id);
  }

  @PostMapping("/turns/{id}/recover")
  public TutorMapper.Turn recover(@PathVariable UUID id, @RequestBody Confirm v) {
    return service.recover(id, v.confirmed());
  }

  @PostMapping(value = "/conversations/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<StreamingResponseBody> stream(
      @PathVariable UUID id,
      @Valid @RequestBody Chat v,
      @RequestHeader("Idempotency-Key") String key) {
    var prepared = service.prepare(id, v, key);
    var context = SecurityContextHolder.getContext();
    StreamingResponseBody body =
        out -> {
          SecurityContextHolder.setContext(context);
          try {
            service.stream(
                prepared,
                (event, data) -> {
                  try {
                    byte[] bytes =
                        ("event: " + event + "\ndata: " + json.writeValueAsString(data) + "\n\n")
                            .getBytes(StandardCharsets.UTF_8);
                    out.write(bytes);
                    out.flush();
                  } catch (IOException e) {
                    throw new AIProvider.Failure("AI_CLIENT_DISCONNECTED", true);
                  }
                });
          } finally {
            SecurityContextHolder.clearContext();
          }
        };
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Accel-Buffering", "no")
        .body(body);
  }
}
