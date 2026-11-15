package dev.scarletnexus.skill;

import java.util.List;
import java.util.UUID;

public final class CatalogModels {
  private CatalogModels() {}

  public record Skill(UUID id, String name, String description, String category, String status) {}

  public record Node(UUID id, String title, String nodeType, String description, String status) {}

  public record Position(UUID nodeId, UUID parentNodeId, int displayOrder) {}

  public record PlacedNode(
      UUID id,
      String title,
      String nodeType,
      String description,
      String status,
      UUID parentNodeId,
      int displayOrder) {}

  public record Relation(UUID id, UUID sourceNodeId, UUID targetNodeId, String relationType) {}

  public record Page<T>(List<T> items, int page, int size, long total) {}

  public record Graph(
      Skill skill, List<PlacedNode> nodes, List<Relation> relations, boolean versioned) {}
}
