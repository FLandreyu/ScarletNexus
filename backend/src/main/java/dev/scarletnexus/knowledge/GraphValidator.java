package dev.scarletnexus.knowledge;

import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class GraphValidator {
  private static final java.util.regex.Pattern UUID_TEXT =
      java.util.regex.Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

  private boolean validUuid(JsonNode value) {
    return value.isTextual() && UUID_TEXT.matcher(value.asText()).matches();
  }

  private final ObjectMapper json;
  private final Validator validator;
  private final Map<Integer, com.networknt.schema.JsonSchema> schemas = new HashMap<>();

  public GraphValidator(ObjectMapper json, Validator validator) {
    this.json =
        json.copy()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    this.validator = validator;
    var factory =
        com.networknt.schema.JsonSchemaFactory.getInstance(
            com.networknt.schema.SpecVersion.VersionFlag.V202012);
    var config =
        com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    for (int version = 1; version <= 2; version++) {
      try (var stream =
          getClass().getResourceAsStream("/schemas/graph-draft-v" + version + ".schema.json")) {
        schemas.put(
            version,
            factory.getSchema(
                new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                com.networknt.schema.InputFormat.JSON,
                config));
      } catch (Exception e) {
        throw new IllegalStateException("Cannot compile graph schema");
      }
    }
  }

  public GraphDocument parse(JsonNode value) {
    try {
      if (value == null
          || !value.isObject()
          || !value.has("schemaVersion")
          || !value.get("schemaVersion").isIntegralNumber()
          || value.toString().length() > 1_000_000) throw new IllegalArgumentException();
      if (!value.path("nodes").isArray() || !value.path("relations").isArray())
        throw new IllegalArgumentException();
      var schema = schemas.get(value.path("schemaVersion").asInt());
      if (schema == null || !schema.validate(value).isEmpty()) throw new IllegalArgumentException();
      if (value.path("schemaVersion").asInt() == 1 && value.has("mappings"))
        throw new IllegalArgumentException();
      if (value.path("schemaVersion").asInt() == 2
          && (!value.path("mappings").isArray() || value.path("mappings").isEmpty()))
        throw new IllegalArgumentException();
      if (value.has("mappings"))
        for (var mapping : value.get("mappings")) {
          var snapshot = json.createObjectNode().put("schemaVersion", 1);
          snapshot.set("nodes", mapping.path("originals"));
          snapshot.set("relations", mapping.path("previousRelations"));
          parse(snapshot);
          if (!validUuid(mapping.path("id"))
              || !mapping.path("kind").isTextual()
              || !mapping.path("origin").isTextual()) throw new IllegalArgumentException();
          for (var target : mapping.path("targets"))
            if (!validUuid(target)) throw new IllegalArgumentException();
          for (var original : mapping.path("originals"))
            if (!validUuid(original.path("id"))
                || (original.hasNonNull("parentNodeId")
                    && !validUuid(original.get("parentNodeId"))))
              throw new IllegalArgumentException();
        }
      for (var node : value.get("nodes")) {
        if (!validUuid(node.path("id"))
            || (node.hasNonNull("parentNodeId") && !validUuid(node.get("parentNodeId"))))
          throw new IllegalArgumentException();
        for (String field : List.of("id", "title", "nodeType", "description", "status"))
          if (!node.path(field).isTextual()) throw new IllegalArgumentException();
        if (node.has("displayOrder")
            && (!node.get("displayOrder").isIntegralNumber()
                || !node.get("displayOrder").canConvertToInt()))
          throw new IllegalArgumentException();
      }
      for (var relation : value.get("relations")) {
        for (String field : List.of("id", "sourceNodeId", "targetNodeId"))
          if (!validUuid(relation.path(field))) throw new IllegalArgumentException();
        for (String field : List.of("id", "sourceNodeId", "targetNodeId", "relationType"))
          if (!relation.path(field).isTextual()) throw new IllegalArgumentException();
      }
      var document = json.treeToValue(value, GraphDocument.class);
      if (!validator.validate(document).isEmpty()) throw new IllegalArgumentException();
      Set<UUID> mappingIds = new HashSet<>();
      for (var m : document.mappings()) {
        if (!mappingIds.add(m.id())
            || new HashSet<>(m.targets()).size() != m.targets().size()
            || m.targets().contains(m.originals().getFirst().id())
            || ("MERGE".equals(m.kind()) && m.targets().size() != 1)
            || ("SPLIT".equals(m.kind()) && m.targets().size() < 2))
          throw new IllegalArgumentException();
      }
      return new GraphDocument(
          document.schemaVersion(),
          document.nodes().stream()
              .map(
                  n ->
                      new GraphDocument.Node(
                          n.id(),
                          n.title().strip(),
                          n.nodeType(),
                          n.description(),
                          n.status(),
                          n.parentNodeId(),
                          n.displayOrder()))
              .toList(),
          document.relations(),
          document.mappings());
    } catch (Exception error) {
      throw new IllegalArgumentException("草案格式无效：检查版本、必填字段、类型和长度限制");
    }
  }

  public List<String> validate(GraphDocument doc, List<GraphDocument.Relation> external) {
    List<String> errors = new ArrayList<>();
    Map<UUID, UUID> parents = new HashMap<>();
    Set<UUID> ids = new HashSet<>();
    for (var node : doc.nodes()) if (!ids.add(node.id())) errors.add("节点ID重复：" + node.id());
    for (var node : doc.nodes()) {
      if (node.parentNodeId() != null && !ids.contains(node.parentNodeId()))
        errors.add("父节点未包含在草案：" + node.id());
      parents.put(node.id(), node.parentNodeId());
    }
    for (UUID id : ids) {
      Set<UUID> seen = new HashSet<>();
      UUID cursor = id;
      while (cursor != null) {
        if (!seen.add(cursor)) {
          errors.add("分类关系成环");
          break;
        }
        cursor = parents.get(cursor);
      }
    }
    List<GraphDocument.Relation> all = new ArrayList<>(external);
    all.addAll(doc.relations());
    Set<UUID> relationIds = new HashSet<>();
    Set<String> pairs = new HashSet<>();
    for (var r : doc.relations()) {
      if (!ids.contains(r.sourceNodeId()) || !ids.contains(r.targetNodeId()))
        errors.add("关系两端必须在草案中");
      if (r.sourceNodeId().equals(r.targetNodeId())) errors.add("关系禁止自引用");
      if (!relationIds.add(r.id())
          || !pairs.add(r.sourceNodeId() + ":" + r.targetNodeId() + ":" + r.relationType()))
        errors.add("关系重复");
    }
    Map<UUID, List<UUID>> edges = new HashMap<>();
    Set<String> allPairs = new HashSet<>();
    for (var r : all)
      if (!allPairs.add(r.sourceNodeId() + ":" + r.targetNodeId() + ":" + r.relationType()))
        errors.add("关系与已有关系重复");
    Map<UUID, Integer> degree = new HashMap<>();
    for (var r : all)
      if ("PREREQUISITE_OF".equals(r.relationType())) {
        edges.computeIfAbsent(r.sourceNodeId(), ignored -> new ArrayList<>()).add(r.targetNodeId());
        degree.putIfAbsent(r.sourceNodeId(), 0);
        degree.merge(r.targetNodeId(), 1, Integer::sum);
      }
    Queue<UUID> queue = new ArrayDeque<>();
    degree.forEach(
        (id, n) -> {
          if (n == 0) queue.add(id);
        });
    int count = 0;
    while (!queue.isEmpty()) {
      UUID id = queue.remove();
      count++;
      for (UUID target : edges.getOrDefault(id, List.of()))
        if (degree.merge(target, -1, Integer::sum) == 0) queue.add(target);
    }
    if (count != degree.size()) errors.add("全局先修依赖成环");
    return errors.stream().distinct().toList();
  }

  public List<String> duplicateCandidates(GraphDocument doc) {
    Map<String, List<UUID>> groups = new LinkedHashMap<>();
    for (var n : doc.nodes()) {
      String key =
          java.text.Normalizer.normalize(n.title(), java.text.Normalizer.Form.NFKC)
              .strip()
              .replaceAll("\\s+", " ")
              .toLowerCase(Locale.ROOT);
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(n.id());
    }
    return groups.entrySet().stream()
        .filter(e -> e.getValue().size() > 1)
        .map(
            e -> "疑似重复名称：" + e.getKey() + "（" + e.getValue().size() + "个节点），可审核后合并：" + e.getValue())
        .toList();
  }
}
