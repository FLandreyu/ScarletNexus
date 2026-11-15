package dev.scarletnexus.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record GraphDocument(
    @Min(1) @Max(1) int schemaVersion,
    @NotNull @Size(max = 500) List<@NotNull @Valid Node> nodes,
    @NotNull @Size(max = 1500) List<@NotNull @Valid Relation> relations) {
  public record Node(
      @NotNull UUID id,
      @NotBlank @Size(max = 160) String title,
      @NotNull @Pattern(regexp = "CATEGORY|CONCEPT|PRACTICE") String nodeType,
      @NotNull @Size(max = 8000) String description,
      @NotNull @Pattern(regexp = "ACTIVE|ARCHIVED") String status,
      UUID parentNodeId,
      @Min(0) int displayOrder) {}

  public record Relation(
      @NotNull UUID id,
      @NotNull UUID sourceNodeId,
      @NotNull UUID targetNodeId,
      @NotNull @Pattern(regexp = "PREREQUISITE_OF|RELATED_TO") String relationType) {}
}
