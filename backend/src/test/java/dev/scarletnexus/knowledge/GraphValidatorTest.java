package dev.scarletnexus.knowledge;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.Test;

class GraphValidatorTest {
  private final ObjectMapper json = new ObjectMapper();

  private GraphDocument.Node node(UUID id, UUID parent) {
    return new GraphDocument.Node(id, "Node", "CONCEPT", "", "ACTIVE", parent, 0);
  }

  @Test
  void rejectsCoercionUnknownFieldsAndWrongVersions() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = new GraphValidator(json, factory.getValidator());
      ObjectNode valid =
          json.valueToTree(new GraphDocument(1, List.of(node(UUID.randomUUID(), null)), List.of()));
      assertThat(validator.parse(valid).schemaVersion()).isEqualTo(1);
      ObjectNode spaced = valid.deepCopy();
      ((ObjectNode) spaced.get("nodes").get(0)).put("title", "  Node  ");
      assertThat(validator.parse(spaced).nodes().getFirst().title()).isEqualTo("Node");
      ObjectNode encoded = valid.deepCopy();
      ((ObjectNode) encoded.get("nodes").get(0))
          .put("id", Base64.getEncoder().encodeToString(new byte[16]));
      assertThatThrownBy(() -> validator.parse(encoded))
          .isInstanceOf(IllegalArgumentException.class);
      ObjectNode number = valid.deepCopy();
      ((ObjectNode) number.get("nodes").get(0)).put("title", 123);
      assertThatThrownBy(() -> validator.parse(number))
          .isInstanceOf(IllegalArgumentException.class);
      ObjectNode fraction = valid.deepCopy();
      ((ObjectNode) fraction.get("nodes").get(0)).put("displayOrder", 0.5);
      assertThatThrownBy(() -> validator.parse(fraction))
          .isInstanceOf(IllegalArgumentException.class);
      ObjectNode unknown = valid.deepCopy();
      unknown.put("executeSQL", "bad");
      assertThatThrownBy(() -> validator.parse(unknown))
          .isInstanceOf(IllegalArgumentException.class);
      valid.put("schemaVersion", 3);
      assertThatThrownBy(() -> validator.parse(valid)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void detectsCategoryAndGlobalDependencyCycles() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = new GraphValidator(json, factory.getValidator());
      UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
      var doc =
          new GraphDocument(
              1,
              List.of(node(a, b), node(b, a)),
              List.of(new GraphDocument.Relation(UUID.randomUUID(), a, b, "PREREQUISITE_OF")));
      var external =
          List.of(
              new GraphDocument.Relation(UUID.randomUUID(), b, c, "PREREQUISITE_OF"),
              new GraphDocument.Relation(UUID.randomUUID(), c, a, "PREREQUISITE_OF"));
      assertThat(validator.validate(doc, external)).contains("分类关系成环", "全局先修依赖成环");
    }
  }

  @Test
  void detectsMissingReferencesAndDuplicateIds() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = new GraphValidator(json, factory.getValidator());
      UUID id = UUID.randomUUID();
      var doc =
          new GraphDocument(1, List.of(node(id, UUID.randomUUID()), node(id, null)), List.of());
      assertThat(validator.validate(doc, List.of()))
          .anyMatch(s -> s.contains("重复"))
          .anyMatch(s -> s.contains("父节点"));
    }
  }
}
