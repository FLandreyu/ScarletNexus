package dev.scarletnexus.ai;

import com.fasterxml.jackson.databind.*;
import java.util.*;

final class StructuredRequest {
  static final int OUTPUT_LIMIT = 8192;

  static String body(
      ObjectMapper json, String model, String instructions, String input, JsonNode schema)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return body(json, model, instructions, input, schema, "json-schema");
  }

  static String body(
      ObjectMapper json,
      String model,
      String instructions,
      String input,
      JsonNode schema,
      String protocol)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    if ("deepseek-json".equals(protocol)) {
      return json.writeValueAsString(
          Map.of(
              "model",
              model,
              "stream",
              false,
              "max_tokens",
              OUTPUT_LIMIT,
              "thinking",
              Map.of("type", "disabled"),
              "response_format",
              Map.of("type", "json_object"),
              "messages",
              List.of(
                  Map.of(
                      "role",
                      "system",
                      "content",
                      instructions
                          + "\nReturn JSON matching this schema (server validation remains mandatory):\n"
                          + schema),
                  Map.of("role", "user", "content", input))));
    }
    if (!"json-schema".equals(protocol))
      throw new IllegalArgumentException("Unsupported AI protocol");
    return json.writeValueAsString(
        Map.of(
            "model",
            model,
            "stream",
            false,
            "max_completion_tokens",
            OUTPUT_LIMIT,
            "messages",
            List.of(
                Map.of("role", "system", "content", instructions),
                Map.of("role", "user", "content", input)),
            "response_format",
            Map.of(
                "type",
                "json_schema",
                "json_schema",
                Map.of("name", "knowledge_draft", "strict", true, "schema", schema))));
  }
}
