package dev.scarletnexus.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record GraphDocument(
    @Min(1) @Max(2) int schemaVersion,
    @NotNull @Size(max = 500) List<@NotNull @Valid Node> nodes,
    @NotNull @Size(max = 1500) List<@NotNull @Valid Relation> relations,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        @Size(max = 100)
        List<@NotNull @Valid Mapping> mappings) {
  public GraphDocument {
    if (mappings == null) mappings = List.of();
  }

  public GraphDocument(int version, List<Node> nodes, List<Relation> relations) {
    this(version, nodes, relations, List.of());
  }

  public record Mapping(
      @NotNull UUID id,
      @NotNull @Pattern(regexp = "MERGE|SPLIT") String kind,
      @NotNull @Pattern(regexp = "MANUAL_DRAFT") String origin,
      @NotNull @Size(min = 1, max = 1) List<@NotNull @Valid Node> originals,
      @NotNull @Size(min = 1, max = 8) List<@NotNull UUID> targets,
      @NotNull @Size(max = 1500) List<@NotNull @Valid Relation> previousRelations) {}

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
