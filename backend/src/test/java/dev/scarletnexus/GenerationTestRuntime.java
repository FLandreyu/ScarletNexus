package dev.scarletnexus;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/** Isolated acceptance fixture. Never packaged in production or used as a real model. */
public final class GenerationTestRuntime implements AutoCloseable {
  static final ObjectMapper JSON = new ObjectMapper();
  final String email = "generation@example.test";
  final String password = UUID.randomUUID().toString();
  final AtomicInteger calls = new AtomicInteger();
  final Path passwordFile;
  final HttpServer provider;
  final EmbeddedPostgres pg;
  final PostgreSQLContainer<?> container;
  final ServletWebServerApplicationContext app;
  final int port;

  GenerationTestRuntime(int requestedPort) throws Exception {
    passwordFile = Files.createTempFile("scarlet-generation-test-", ".secret");
    Files.writeString(passwordFile, password);
    provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    provider.createContext(
        "/v1/chat/completions",
        x -> {
          calls.incrementAndGet();
          var request = JSON.readTree(x.getRequestBody());
          String topic = request.path("messages").path(1).path("content").asText();
          String doc =
              JSON.writeValueAsString(
                  Map.of(
                      "schemaVersion",
                      1,
                      "nodes",
                      List.of(
                          Map.of(
                              "id",
                              UUID.randomUUID().toString(),
                              "title",
                              "本地协议验收节点",
                              "nodeType",
                              "CONCEPT",
                              "description",
                              "仅为协议测试数据",
                              "status",
                              "ACTIVE",
                              "displayOrder",
                              0)),
                      "relations",
                      List.of()));
          var graph = JSON.readTree(doc);
          ((com.fasterxml.jackson.databind.node.ObjectNode) graph.get("nodes").get(0))
              .putNull("parentNodeId");
          doc = graph.toString();
          if (topic.contains("forged-mapping")) {
            var forged = (com.fasterxml.jackson.databind.node.ObjectNode) graph;
            forged.put("schemaVersion", 2);
            var original = forged.path("nodes").get(0).deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) original)
                .put("id", UUID.randomUUID().toString());
            var mapping = forged.putArray("mappings").addObject();
            mapping
                .put("id", UUID.randomUUID().toString())
                .put("kind", "MERGE")
                .put("origin", "MANUAL_DRAFT");
            mapping.putArray("originals").add(original);
            mapping.putArray("targets").add(forged.path("nodes").get(0).path("id").asText());
            mapping.putArray("previousRelations");
            doc = forged.toString();
          }
          if (topic.contains("always-invalid")
              || (topic.contains("invalid") && !topic.contains("[Repair request")))
            doc = "{\"schemaVersion\":99}";
          var response =
              Map.of(
                  "choices",
                  List.of(
                      Map.of(
                          "finish_reason",
                          topic.contains("truncated") ? "length" : "stop",
                          "message",
                          Map.of("content", doc))),
                  "usage",
                  Map.of("prompt_tokens", 12, "completion_tokens", 5));
          byte[] bytes = JSON.writeValueAsBytes(response);
          x.getResponseHeaders().set("Content-Type", "application/json");
          x.sendResponseHeaders(200, bytes.length);
          try (var out = x.getResponseBody()) {
            out.write(bytes);
          }
        });
    provider.start();
    String url, user, pass;
    if ("container".equals(System.getProperty("test.database"))) {
      container = new PostgreSQLContainer<>("postgres:17.11-alpine");
      container.start();
      pg = null;
      url = container.getJdbcUrl();
      user = container.getUsername();
      pass = container.getPassword();
    } else {
      container = null;
      pg = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start();
      url = pg.getJdbcUrl("postgres", "postgres");
      user = "postgres";
      pass = "";
    }
    app =
        (ServletWebServerApplicationContext)
            new SpringApplicationBuilder(ScarletNexusApplication.class)
                .run(
                    "--server.port=" + requestedPort,
                    "--server.address=127.0.0.1",
                    "--debug=false",
                    "--spring.datasource.url=" + url,
                    "--spring.datasource.username=" + user,
                    "--spring.datasource.password=" + pass,
                    "--app.bootstrap.email=" + email,
                    "--app.bootstrap.password-file=" + passwordFile,
                    "--app.ai.enabled=true",
                    "--app.ai.generation-enabled=true",
                    "--app.ai.model=local-protocol-fixture",
                    "--app.ai.key=test-only-not-a-provider-key",
                    "--app.ai.base-url=http://127.0.0.1:" + provider.getAddress().getPort() + "/v1",
                    "--app.ai.retries=0",
                    "--app.ai.tokens.daily-limit=1000000",
                    "--app.ai.budget.enabled=true",
                    "--app.ai.budget.currency=USD",
                    "--app.ai.budget.provider-label=local-protocol-fixture",
                    "--app.ai.budget.price-version=test-v1",
                    "--app.ai.budget.price-date=2026-01-01",
                    "--app.ai.budget.input-per-million=2",
                    "--app.ai.budget.output-per-million=4",
                    "--app.ai.budget.per-job-reserve=1",
                    "--app.ai.budget.daily-limit=5");
    port = app.getWebServer().getPort();
  }

  public void close() throws Exception {
    try {
      app.close();
    } finally {
      provider.stop(0);
      if (pg != null) pg.close();
      if (container != null) container.stop();
      Files.deleteIfExists(passwordFile);
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2)
      throw new IllegalArgumentException("Metadata path and stop-file path required");
    Path metadata = Path.of(args[0]), stop = Path.of(args[1]);
    if (Files.exists(stop)) throw new IllegalArgumentException("Use a new stop-file path");
    try (var runtime = new GenerationTestRuntime(18080)) {
      Files.writeString(
          metadata,
          JSON.writeValueAsString(
              Map.of(
                  "port",
                  runtime.port,
                  "email",
                  runtime.email,
                  "passwordFile",
                  runtime.passwordFile.toString())),
          StandardCharsets.UTF_8);
      while (!Files.exists(stop)) Thread.sleep(250);
    } finally {
      Files.deleteIfExists(metadata);
    }
  }
}
