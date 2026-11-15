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

  public record Publish(@NotNull @Min(0) Integer revision, boolean confirmed) {}

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
  public DraftService.Preview validate(@PathVariable UUID id) {
    return drafts.preview(id);
  }

  @PostMapping("/graph/drafts/{id}/publish")
  public DraftMapper.Version publish(
      @PathVariable UUID id,
      @Valid @RequestBody Publish value,
      @RequestHeader("Idempotency-Key") String key) {
    return drafts.publish(id, value.revision(), value.confirmed(), key);
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
