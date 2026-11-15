package dev.scarletnexus.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DraftController {
  private final DraftService drafts;

  public DraftController(DraftService drafts) {
    this.drafts = drafts;
  }

  public record Save(@NotNull @Min(0) Integer revision, @NotNull JsonNode document) {}

  public record Publish(
      @NotNull @Min(0) Integer revision, boolean confirmed, boolean includeAffectedSkills) {}

  public record Scope(boolean includeAffectedSkills) {}

  public record Transform(
      @NotNull @Min(0) Integer revision,
      @NotNull @Pattern(regexp = "MERGE|SPLIT") String kind,
      @NotNull UUID source,
      UUID target,
      @Size(max = 8) List<@NotBlank @Size(max = 160) String> titles) {}

  @PostMapping("/graph/drafts/{id}/transform")
  public DraftMapper.Draft transform(@PathVariable UUID id, @Valid @RequestBody Transform request) {
    return drafts.transform(
        id,
        request.revision(),
        request.kind(),
        request.source(),
        request.target(),
        request.titles());
  }

  @PostMapping("/skills/{id}/graph/drafts")
  public DraftMapper.Draft create(@PathVariable UUID id) {
    return drafts.create(id);
  }

  @GetMapping("/skills/{id}/graph/drafts")
  public List<DraftMapper.Draft> list(@PathVariable UUID id) {
    return drafts.list(id);
  }

  @GetMapping("/graph/drafts/{id}")
  public DraftMapper.Draft get(@PathVariable UUID id) {
    return drafts.get(id);
  }

  @PatchMapping("/graph/drafts/{id}")
  public DraftMapper.Draft save(@PathVariable UUID id, @Valid @RequestBody Save value) {
    return drafts.save(id, value.revision(), value.document());
  }

  @PostMapping("/graph/drafts/{id}/validate")
  public DraftService.Preview validate(
      @PathVariable UUID id, @RequestBody(required = false) Scope scope) {
    return drafts.preview(id, scope != null && scope.includeAffectedSkills());
  }

  @PostMapping("/graph/drafts/{id}/publish")
  public DraftMapper.Version publish(
      @PathVariable UUID id,
      @Valid @RequestBody Publish value,
      @RequestHeader("Idempotency-Key") String key) {
    return drafts.publish(
        id, value.revision(), value.confirmed(), key, value.includeAffectedSkills());
  }

  @GetMapping("/skills/{id}/graph/versions")
  public List<DraftMapper.Version> versions(@PathVariable UUID id) {
    return drafts.versions(id);
  }

  @GetMapping("/graph/versions/{id}")
  public DraftMapper.Version version(@PathVariable UUID id) {
    return drafts.version(id);
  }
}
