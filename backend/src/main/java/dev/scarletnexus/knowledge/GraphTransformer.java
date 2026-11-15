package dev.scarletnexus.knowledge;

import dev.scarletnexus.common.DomainFailure;
import java.util.*;
import java.util.function.UnaryOperator;

/** Pure candidate transformations; no node definition or evidence is deleted. */
public final class GraphTransformer {
  private GraphTransformer() {}

  public static GraphDocument transform(
      GraphDocument doc, String kind, UUID source, UUID target, List<String> titles) {
    DomainFailure.require(doc.mappings().size() < 100, "MAPPING_LIMIT", "单份草案最多记录100次合并拆分");
    var original =
        doc.nodes().stream()
            .filter(n -> n.id().equals(source))
            .findFirst()
            .orElseThrow(DomainFailure::missing);
    List<GraphDocument.Node> nodes = new ArrayList<>();
    List<UUID> targets = new ArrayList<>();
    if ("MERGE".equals(kind)) {
      DomainFailure.require(
          target != null
              && !target.equals(source)
              && doc.nodes().stream().anyMatch(n -> n.id().equals(target)),
          "INVALID_MERGE",
          "选择两个不同节点，目标节点ID将保留");
      targets.add(target);
    } else {
      DomainFailure.require(
          "SPLIT".equals(kind)
              && titles != null
              && titles.size() >= 2
              && titles.size() <= 8
              && titles.stream()
                  .allMatch(t -> t != null && !t.isBlank() && t.strip().length() <= 160)
              && titles.stream().map(String::strip).distinct().count() == titles.size(),
          "INVALID_SPLIT",
          "拆分需2至8个不同的新节点名称");
      DomainFailure.require(
          doc.nodes().size() - 1 + titles.size() <= 500, "NODE_LIMIT", "转换后节点不能超过500个");
      for (String title : titles) {
        UUID id = UUID.randomUUID();
        targets.add(id);
        nodes.add(
            new GraphDocument.Node(
                id,
                title.strip(),
                original.nodeType(),
                original.description(),
                original.status(),
                original.parentNodeId(),
                original.displayOrder()));
      }
    }
    UUID primary = targets.getFirst();
    for (var n : doc.nodes()) {
      if (n.id().equals(source)) continue;
      UUID parent = n.parentNodeId();
      if (source.equals(parent))
        parent = n.id().equals(primary) ? original.parentNodeId() : primary;
      nodes.add(
          new GraphDocument.Node(
              n.id(),
              n.title(),
              n.nodeType(),
              n.description(),
              n.status(),
              parent,
              n.displayOrder()));
    }
    UnaryOperator<UUID> rewire = id -> id.equals(source) ? primary : id;
    List<GraphDocument.Relation> relations = new ArrayList<>(), before = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (var r : doc.relations()) {
      if (r.sourceNodeId().equals(source) || r.targetNodeId().equals(source)) before.add(r);
      UUID a = rewire.apply(r.sourceNodeId()), b = rewire.apply(r.targetNodeId());
      if (a.equals(b)) continue;
      String key = a + ":" + b + ":" + r.relationType();
      if ("RELATED_TO".equals(r.relationType()) && a.compareTo(b) > 0)
        key = b + ":" + a + ":" + r.relationType();
      if (seen.add(key)) relations.add(new GraphDocument.Relation(r.id(), a, b, r.relationType()));
    }
    var mappings = new ArrayList<>(doc.mappings());
    mappings.add(
        new GraphDocument.Mapping(
            UUID.randomUUID(), kind, "MANUAL_DRAFT", List.of(original), targets, before));
    return new GraphDocument(2, nodes, relations, mappings);
  }
}
