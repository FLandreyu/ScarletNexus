package dev.scarletnexus.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;

class OpenAiCompatibleProviderTest {
  private final ObjectMapper json = new ObjectMapper();
  private HttpServer server;
  private String reply;
  private int status;
  private volatile long delayMillis;
  private final AtomicInteger calls = new AtomicInteger();
  private JsonNode request;

  @BeforeEach
  void start() throws Exception {
    status = 200;
    reply =
        json.writeValueAsString(
            java.util.Map.of(
                "choices",
                    java.util.List.of(
                        java.util.Map.of(
                            "finish_reason",
                            "stop",
                            "message",
                            java.util.Map.of("content", "{\"nodes\":[]}"))),
                "usage", java.util.Map.of("prompt_tokens", 12, "completion_tokens", 5)));
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          calls.incrementAndGet();
          assertEquals(
              "Bearer local-test-only", exchange.getRequestHeaders().getFirst("Authorization"));
          request = json.readTree(exchange.getRequestBody());
          if (delayMillis > 0) {
            try {
              Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }
          }
          byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Retry-After", "0");
          exchange.sendResponseHeaders(status, bytes.length);
          try (var stream = exchange.getResponseBody()) {
            stream.write(bytes);
          }
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private OpenAiCompatibleProvider provider(int retries) {
    return new OpenAiCompatibleProvider(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
        "configured-model",
        "local-test-only",
        Duration.ofSeconds(2),
        retries,
        json);
  }

  private AIProvider.Result generate(AIProvider p) {
    return p.generateStructured("Return JSON", "topic", new GraphGenerationContract(json).schema());
  }

  @Test
  void sendsRealHttpContractAndReadsUsage() {
    var result = generate(provider(0));
    assertTrue(result.document().path("nodes").isArray());
    assertEquals(12L, result.usage().inputTokens());
    assertEquals("PROVIDER", result.usage().source());
    assertEquals("configured-model", request.path("model").asText());
    assertTrue(request.path("response_format").path("json_schema").path("strict").asBoolean());
    assertEquals("topic", request.path("messages").path(1).path("content").asText());
  }

  @Test
  void boundsRetryAndDoesNotRetryAuthentication() {
    status = 429;
    assertEquals(
        "AI_TEMPORARY_FAILURE",
        assertThrows(AIProvider.Failure.class, () -> generate(provider(2))).code());
    assertEquals(3, calls.get());
    status = 401;
    reply = "secret provider details";
    var failure = assertThrows(AIProvider.Failure.class, () -> generate(provider(2)));
    assertEquals("AI_AUTH_FAILED", failure.getMessage());
    assertEquals(4, calls.get());
  }

  @Test
  void rejectsRefusalTruncationMalformedAndTrailingJson() {
    for (String invalid :
        new String[] {
          "garbage",
          "{} {}",
          "{\"choices\":[{\"finish_reason\":\"length\"}]}",
          "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"refusal\":\"no\"}}]}",
          "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{} {}\"}}]}"
        }) {
      reply = invalid;
      assertThrows(AIProvider.Failure.class, () -> generate(provider(0)));
    }
  }

  @Test
  void missingUsageIsUnknownNotZero() throws Exception {
    var root = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(reply);
    root.remove("usage");
    reply = root.toString();
    var result = generate(provider(0));
    assertNull(result.usage().inputTokens());
    assertEquals("UNKNOWN", result.usage().source());
  }

  @Test
  void disabledProviderNeverContactsNetwork() {
    var p = new AiConfiguration().aiProvider(new MockEnvironment(), json);
    assertEquals(
        "AI_NOT_CONFIGURED", assertThrows(AIProvider.Failure.class, () -> generate(p)).code());
    assertEquals(0, calls.get());
  }

  @Test
  void refusesInsecureExternalEndpointsAndUnboundedConfiguration() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OpenAiCompatibleProvider(
                URI.create("http://example.com/v1"), "m", "key", Duration.ofSeconds(1), 0, json));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OpenAiCompatibleProvider(
                URI.create("https://example.com/v1?key=hidden"),
                "m",
                "key",
                Duration.ofSeconds(1),
                0,
                json));
    assertEquals(2000, OpenAiCompatibleProvider.retryDelay("2", 0));
  }

  @Test
  void rejectsOversizeResponse() {
    reply = "x".repeat(2_000_001);
    assertThrows(AIProvider.Failure.class, () -> generate(provider(0)));
    assertEquals(1, calls.get());
  }

  @Test
  void timeoutDoesNotAutomaticallyRepeatAnUncertainCall() {
    delayMillis = 500;
    var p =
        new OpenAiCompatibleProvider(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
            "configured-model",
            "local-test-only",
            Duration.ofMillis(100),
            2,
            json);
    assertEquals("AI_TIMEOUT", assertThrows(AIProvider.Failure.class, () -> generate(p)).code());
    assertEquals(1, calls.get());
  }
}
