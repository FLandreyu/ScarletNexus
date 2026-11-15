package dev.scarletnexus.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;

/** Converts the bundled v1 contract only, not a general JSON Schema compiler. */
@Component
public class GraphGenerationContract {
  public static final String INSTRUCTIONS =
      "Return a knowledge graph JSON candidate only, schema version 1. Use canonical UUID identifiers, nonblank titles up to 160 characters, descriptions up to 8000 characters, at most 500 nodes and 1500 relations. Parent may be null. Do not create cycles. Never publish data.";
  private final JsonNode schema;

  public GraphGenerationContract(ObjectMapper json) {
    try (var stream = getClass().getResourceAsStream("/schemas/graph-draft-v1.schema.json")) {
      schema = convert(json.readTree(stream), json);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot load graph generation contract");
    }
  }

  public JsonNode schema() {
    return schema.deepCopy();
  }

  private static JsonNode convert(JsonNode source, ObjectMapper json) {
    ObjectNode target = json.createObjectNode();
    if (source.has("const")) {
      target.put("type", "integer");
      target.putArray("enum").add(source.get("const"));
    } else if (source.has("type")) target.set("type", source.get("type"));
    if (source.has("enum")) {
      target.set("enum", source.get("enum"));
      target.put("type", "string");
    }
    if (source.has("properties")) {
      ObjectNode properties = target.putObject("properties");
      ArrayNode required = target.putArray("required");
      source
          .get("properties")
          .fields()
          .forEachRemaining(
              e -> {
                properties.set(e.getKey(), convert(e.getValue(), json));
                required.add(e.getKey());
              });
      target.put("additionalProperties", false);
    }
    if (source.has("items")) target.set("items", convert(source.get("items"), json));
    if (source.has("anyOf")) {
      ArrayNode alternatives = target.putArray("anyOf");
      source.get("anyOf").forEach(n -> alternatives.add(convert(n, json)));
    }
    return target;
  }
}
