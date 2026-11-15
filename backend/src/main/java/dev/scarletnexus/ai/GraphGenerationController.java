package dev.scarletnexus.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class GraphGenerationController {
  private final ConfiguredGeneration generation;

  public GraphGenerationController(ConfiguredGeneration generation) {
    this.generation = generation;
  }

  public record Availability(boolean enabled) {}

  public record Input(@NotBlank @Size(max = 50000) String topic) {}

  public record Repair(boolean confirmed) {}

  @PostMapping("/ai/jobs/{id}/repair")
  public GenerationJobs.View repair(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Repair request) {
    return generation.repair(id, key, request.confirmed());
  }

  @GetMapping("/ai/generation")
  public Availability status() {
    return new Availability(generation.enabled());
  }

  @PostMapping("/skills/{id}/graph/generation")
  public GenerationJobs.View generate(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Input request) {
    return generation.generate(id, key, request.topic());
  }
}
