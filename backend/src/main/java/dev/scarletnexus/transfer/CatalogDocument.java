package dev.scarletnexus.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public record CatalogDocument(
    @NotNull @Pattern(regexp = "scarlet-nexus-catalog/v1") String format,
    @NotEmpty @Size(max = 100) List<@NotNull @Valid Skill> skills,
    @NotNull @Size(max = 1000) List<@NotNull @Valid Node> nodes,
    @NotNull @Size(max = 5000) List<@NotNull @Valid Position> positions,
    @NotNull @Size(max = 3000) List<@NotNull @Valid Relation> relations) {
  public record Skill(
      @NotNull UUID id,
      @NotBlank @Size(max = 120) String name,
      @NotNull @Size(max = 4000) String description,
      @NotNull @Size(max = 80) String category,
      @NotNull @Pattern(regexp = "ACTIVE|ARCHIVED") String status) {}

  public record Node(
      @NotNull UUID id,
      @NotBlank @Size(max = 160) String title,
      @NotNull @Pattern(regexp = "CATEGORY|CONCEPT|PRACTICE") String nodeType,
      @NotNull @Size(max = 8000) String description,
      @NotNull @Pattern(regexp = "ACTIVE|ARCHIVED") String status) {}

  public record Position(
      @NotNull UUID skillId, @NotNull UUID nodeId, UUID parentNodeId, @Min(0) int displayOrder) {}

  public record Relation(
      @NotNull UUID id,
      @NotNull UUID sourceNodeId,
      @NotNull UUID targetNodeId,
      @NotNull @Pattern(regexp = "PREREQUISITE_OF|RELATED_TO") String relationType) {}
}
