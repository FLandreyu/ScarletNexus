package dev.scarletnexus.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class ChatStreamTransportTest {
  HttpServer server;
  ExecutorService executor;

  @AfterEach
  void close() {
    if (server != null) server.stop(0);
    if (executor != null) executor.shutdownNow();
  }

  OpenAiCompatibleProvider provider(Duration timeout) throws Exception {
    return new OpenAiCompatibleProvider(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
        "test-stream",
        "fixture-key",
        timeout,
        0,
        new ObjectMapper(),
        "deepseek-json");
  }

  void server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext("/chat/completions", handler);
    server.start();
  }

  @Test
  void consumesUtf8AcrossByteBoundariesBeforeTheFinalEvent() throws Exception {
    var received = new CountDownLatch(1);
    server(
        x -> {
          try {
            assertEquals("Bearer fixture-key", x.getRequestHeaders().getFirst("Authorization"));
            var request = new ObjectMapper().readTree(x.getRequestBody());
            assertTrue(request.path("stream").asBoolean());
            assertTrue(request.path("stream_options").path("include_usage").asBoolean());
            x.getResponseHeaders().set("Content-Type", "text/event-stream");
            x.sendResponseHeaders(200, 0);
            var out = x.getResponseBody();
            for (byte b :
                "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                    .getBytes(StandardCharsets.UTF_8)) {
              out.write(b);
              out.flush();
            }
            assertTrue(received.await(1, TimeUnit.SECONDS), "Delta must arrive before finish");
            out.write(
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2}}\n\ndata: [DONE]\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.close();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    var text = new StringBuffer();
    var usage =
        provider(Duration.ofSeconds(3))
            .chatStream(
                List.of(new AIProvider.Message("user", "hi")),
                chunk -> {
                  text.append(chunk);
                  received.countDown();
                });
    assertEquals("你好", text.toString());
    assertEquals(10, usage.inputTokens());
    assertEquals(2, usage.outputTokens());
  }

  @Test
  void totalDeadlineIncludesAnUnfinishedResponseBody() throws Exception {
    server(
        x -> {
          try {
            x.sendResponseHeaders(200, 0);
            x.getResponseBody()
                .write(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"
                        .getBytes(StandardCharsets.UTF_8));
            x.getResponseBody().flush();
            Thread.sleep(1000);
            x.close();
          } catch (Exception ignored) {
          }
        });
    var p = provider(Duration.ofMillis(200));
    long start = System.nanoTime();
    var failure =
        assertThrows(
            AIProvider.Failure.class,
            () -> p.chatStream(List.of(new AIProvider.Message("user", "hi")), text -> {}));
    assertEquals("AI_TIMEOUT", failure.code());
    assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 900);
  }

  @Test
  void missingDoneAndLengthFinishNeverBecomeSuccessfulMessages() throws Exception {
    server(
        x -> {
          byte[] bytes =
              "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":\"length\"}]}\n\n"
                  .getBytes(StandardCharsets.UTF_8);
          x.sendResponseHeaders(200, bytes.length);
          x.getResponseBody().write(bytes);
          x.close();
        });
    var p = provider(Duration.ofSeconds(2));
    var failure =
        assertThrows(
            AIProvider.Failure.class,
            () -> p.chatStream(List.of(new AIProvider.Message("user", "hi")), text -> {}));
    assertEquals("AI_INCOMPLETE_OUTPUT", failure.code());
  }
}
