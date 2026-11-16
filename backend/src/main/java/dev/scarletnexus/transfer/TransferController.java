package dev.scarletnexus.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import dev.scarletnexus.transfer.TransferService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/data")
public class TransferController {
  private final TransferService service;

  public TransferController(TransferService service) {
    this.service = service;
  }

  public record PreviewWrite(@NotBlank @Size(max = 2097152) String documentText) {}

  public record Confirm(
      @Min(0) int revision,
      @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String sourceHash,
      boolean confirmed) {}

  @GetMapping("/catalog")
  public ResponseEntity<CatalogDocument> catalog(@RequestParam(required = false) UUID skillId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Content-Disposition", "attachment; filename=\"scarlet-nexus-catalog.json\"")
        .body(service.catalog(skillId));
  }

  @GetMapping("/snapshot")
  public ResponseEntity<JsonNode> snapshot() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            "Content-Disposition", "attachment; filename=\"scarlet-nexus-learning-snapshot.json\"")
        .body(service.snapshot());
  }

  @PostMapping("/catalog/imports")
  public Preview preview(@Valid @RequestBody PreviewWrite v) {
    return service.preview(v.documentText());
  }

  @GetMapping("/catalog/imports/{id}")
  public Preview preview(@PathVariable UUID id) {
    return service.preview(id);
  }

  @PostMapping("/catalog/imports/{id}/confirm")
  public Result confirm(
      @PathVariable UUID id,
      @Valid @RequestBody Confirm v,
      @RequestHeader("Idempotency-Key") String key) {
    return service.confirm(id, v.revision(), v.sourceHash(), v.confirmed(), key);
  }
}
