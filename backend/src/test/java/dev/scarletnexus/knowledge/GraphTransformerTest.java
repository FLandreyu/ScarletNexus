package dev.scarletnexus.knowledge;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class GraphTransformerTest {
  @Test
  void schemaAndDuplicateChecksApplyToVersionedMappings() throws Exception {
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      var validator = new GraphValidator(json, factory.getValidator());
      UUID a = UUID.randomUUID(), b = UUID.randomUUID();
      var doc =
          new GraphDocument(1, List.of(node(a, null, "ABC"), node(b, null, "ＡＢＣ")), List.of());
      assertThat(validator.duplicateCandidates(doc)).hasSize(1);
      var changed = GraphTransformer.transform(doc, "MERGE", a, b, List.of());
      assertThat(validator.parse(json.valueToTree(changed)).mappings()).hasSize(1);
      var invalid = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(changed);
      invalid.put("unexpected", true);
      assertThatThrownBy(() -> validator.parse(invalid))
          .isInstanceOf(IllegalArgumentException.class);
      invalid.remove("unexpected");
      ((com.fasterxml.jackson.databind.node.ObjectNode)
              invalid.path("mappings").get(0).path("originals").get(0))
          .put("title", true);
      assertThatThrownBy(() -> validator.parse(invalid))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  GraphDocument.Node node(UUID id, UUID parent, String title) {
    return new GraphDocument.Node(id, title, "CONCEPT", "original text", "ACTIVE", parent, 0);
  }

  @Test
  void mergeKeepsTargetAndRewiresWhilePreservingSourceSnapshot() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), child = UUID.randomUUID();
    var edge = new GraphDocument.Relation(UUID.randomUUID(), a, b, "PREREQUISITE_OF");
    var doc =
        new GraphDocument(
            1, List.of(node(a, null, "A"), node(b, a, "B"), node(child, a, "C")), List.of(edge));
    var changed = GraphTransformer.transform(doc, "MERGE", a, b, List.of());
    assertThat(changed.schemaVersion()).isEqualTo(2);
    assertThat(changed.nodes()).extracting(GraphDocument.Node::id).containsExactly(b, child);
    assertThat(changed.nodes().getFirst().parentNodeId()).isNull();
    assertThat(changed.nodes().getLast().parentNodeId()).isEqualTo(b);
    assertThat(changed.relations()).isEmpty();
    assertThat(changed.mappings().getFirst().originals().getFirst().description())
        .isEqualTo("original text");
    assertThat(changed.mappings().getFirst().previousRelations()).containsExactly(edge);
    assertThat(doc.nodes()).hasSize(3);
  }

  @Test
  void splitCreatesNewIdsAndRoutesExistingChildrenToFirstReplacement() {
    UUID a = UUID.randomUUID(), child = UUID.randomUUID();
    var doc = new GraphDocument(1, List.of(node(a, null, "A"), node(child, a, "C")), List.of());
    var changed = GraphTransformer.transform(doc, "SPLIT", a, null, List.of("first", "second"));
    assertThat(changed.nodes()).hasSize(3);
    assertThat(changed.nodes()).extracting(GraphDocument.Node::id).doesNotContain(a);
    assertThat(changed.nodes().getLast().parentNodeId()).isEqualTo(changed.nodes().getFirst().id());
    assertThat(changed.mappings().getFirst().targets()).hasSize(2);
  }

  @Test
  void rejectsSameNodeMergeAndDuplicateSplitNames() {
    UUID a = UUID.randomUUID();
    var doc = new GraphDocument(1, List.of(node(a, null, "A")), List.of());
    assertThatThrownBy(() -> GraphTransformer.transform(doc, "MERGE", a, a, List.of()))
        .isInstanceOf(dev.scarletnexus.common.DomainFailure.class);
    assertThatThrownBy(
            () -> GraphTransformer.transform(doc, "SPLIT", a, null, List.of("one", " one ")))
        .isInstanceOf(dev.scarletnexus.common.DomainFailure.class);
  }
}
