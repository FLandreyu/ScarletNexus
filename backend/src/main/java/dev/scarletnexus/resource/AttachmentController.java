package dev.scarletnexus.resource;

import dev.scarletnexus.assessment.PracticeModels.Attachment;
import dev.scarletnexus.learning.LearningModels.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AttachmentController {
  private final AttachmentService service;

  public AttachmentController(AttachmentService service) {
    this.service = service;
  }

  public record State(@NotNull @Pattern(regexp = "READY|ARCHIVED") String status) {}

  @GetMapping("/learning/tasks/{id}/attachments")
  public Page<Attachment> list(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.list(id, page, size);
  }

  @PostMapping(
      value = "/learning/tasks/{id}/attachments",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Attachment upload(@PathVariable UUID id, @RequestParam MultipartFile file)
      throws IOException {
    return service.upload(id, file);
  }

  @GetMapping("/attachments/{id}/content")
  public ResponseEntity<byte[]> content(@PathVariable UUID id) throws IOException {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Disposition", "inline; filename=\"" + id + ".png\"")
        .cacheControl(CacheControl.noStore())
        .body(service.content(id));
  }

  @PatchMapping("/attachments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void state(@PathVariable UUID id, @Valid @RequestBody State v) {
    service.state(id, v.status());
  }
}
