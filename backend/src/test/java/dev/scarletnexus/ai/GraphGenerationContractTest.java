package dev.scarletnexus.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class GraphGenerationContractTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void normalizesOnlyTheProviderSchemaAndRequiresAllFields() throws Exception {
    JsonNode schema = new GraphGenerationContract(json).schema();
    JsonNode node = schema.path("properties").path("nodes").path("items");
    assertEquals(7, node.path("required").size());
    assertFalse(node.path("additionalProperties").asBoolean(true));
    assertEquals(
        "null",
        node.path("properties").path("parentNodeId").path("anyOf").path(1).path("type").asText());
    assertFalse(schema.toString().contains("const"));
    assertFalse(schema.toString().contains("maxLength"));
    assertEquals(1, schema.path("properties").path("schemaVersion").path("enum").path(0).asInt());
    try (var stream = getClass().getResourceAsStream("/schemas/graph-draft-v1.schema.json")) {
      assertEquals(
          160,
          json.readTree(stream)
              .path("properties")
              .path("nodes")
              .path("items")
              .path("properties")
              .path("title")
              .path("maxLength")
              .asInt());
    }
  }

  @Test
  void reservesFromSerializedUtf8IncludingSchemaAndOutput() throws Exception {
    var policy =
        new TokenReservationPolicy(
            new MockEnvironment().withProperty("app.ai.tokens.daily-limit", "100000"));
    String body =
        StructuredRequest.body(
            json,
            "test-model",
            GraphGenerationContract.INSTRUCTIONS,
            "主题\n😀",
            new GraphGenerationContract(json).schema());
    assertEquals(body.getBytes(StandardCharsets.UTF_8).length + 4096 + 8192, policy.reserve(body));
    assertThrows(
        dev.scarletnexus.common.DomainFailure.class, () -> policy.reserve("x".repeat(100001)));
  }

  @Test
  void unsetBudgetAndDisabledGenerationNeverSend() {
    var env = new MockEnvironment();
    var policy = new TokenReservationPolicy(env);
    assertThrows(dev.scarletnexus.common.DomainFailure.class, () -> policy.reserve("{}"));
    var configured = new ConfiguredGeneration(env, policy, null, null, null, null, json);
    var failure =
        assertThrows(
            dev.scarletnexus.common.DomainFailure.class,
            () -> configured.generate(java.util.UUID.randomUUID(), "key", "topic"));
    assertEquals("AI_NOT_CONFIGURED", failure.code());
  }
}
