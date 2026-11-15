package dev.scarletnexus;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TutorIT {
  LearningIT h;
  PracticeIT p;
  HttpServer server;
  ExecutorService executor;
  AtomicInteger calls = new AtomicInteger();
  int requestedPort;

  @BeforeAll
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext(
        "/chat/completions",
        x -> {
          try {
            calls.incrementAndGet();
            var body = new ObjectMapper().readTree(x.getRequestBody());
            var messages = body.path("messages");
            String text = messages.get(messages.size() - 1).path("content").asText();
            x.getResponseHeaders().set("Content-Type", "text/event-stream");
            x.sendResponseHeaders(200, 0);
            var out = x.getResponseBody();
            out.write(
                "data: {\"choices\":[{\"delta\":{\"content\":\"测试协议增量：\"}}]}\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            if (text.equals("timeout")) {
              Thread.sleep(1800);
              x.close();
              return;
            }
            String finish = text.equals("fail") ? "length" : "stop";
            out.write(
                ("data: {\"choices\":[{\"delta\":{\"content\":\"请先说明你的思路。\"},\"finish_reason\":\""
                        + finish
                        + "\"}],\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":8}}\n\ndata: [DONE]\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.close();
          } catch (Exception ignored) {
            x.close();
          }
        });
    server.createContext(
        "/calls",
        x -> {
          byte[] bytes = ("{\"calls\":" + calls.get() + "}").getBytes(StandardCharsets.UTF_8);
          x.sendResponseHeaders(200, bytes.length);
          x.getResponseBody().write(bytes);
          x.close();
        });
    server.start();
    Map<String, String> config = new HashMap<>();
    config.put("app.ai.enabled", "true");
    config.put("app.ai.tutor-enabled", "true");
    config.put("app.ai.key", "local-stream-fixture-only");
    config.put("app.ai.model", "local-stream-fixture");
    config.put("app.ai.protocol", "deepseek-json");
    config.put("app.ai.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
    config.put("app.ai.timeout-seconds", "1");
    config.put("app.ai.retries", "0");
    config.put("app.ai.tokens.daily-limit", "1000000");
    config.put("app.ai.budget.enabled", "true");
    config.put("app.ai.budget.currency", "USD");
    config.put("app.ai.budget.provider-label", "test-only");
    config.put("app.ai.budget.price-version", "test-only");
    config.put("app.ai.budget.price-date", "2026-01-01");
    config.put("app.ai.budget.input-per-million", "1");
    config.put("app.ai.budget.output-per-million", "2");
    config.put("app.ai.budget.per-job-reserve", "1");
    config.put("app.ai.budget.daily-limit", "10");
    h = new LearningIT();
    h.runtime = new LearningTestRuntime(requestedPort, config);
    h.jdbc = h.runtime.app.getBean(JdbcTemplate.class);
    h.c = h.login(h.runtime.email, h.runtime.password);
    p = new PracticeIT();
    p.h = h;
  }

  @AfterAll
  void close() throws Exception {
    if (h != null) h.stop();
    if (server != null) server.stop(0);
    if (executor != null) executor.shutdownNow();
  }

  public static void main(String[] args) throws Exception {
    var fixture = new TutorIT();
    fixture.requestedPort = 18080;
    var metadata = java.nio.file.Path.of(args[0]);
    var stop = java.nio.file.Path.of(args[1]);
    if (java.nio.file.Files.exists(stop)) throw new IllegalArgumentException("Use a new stop path");
    try {
      fixture.start();
      java.nio.file.Files.writeString(
          metadata,
          new ObjectMapper()
              .writeValueAsString(
                  Map.of(
                      "port",
                      fixture.h.runtime.port,
                      "email",
                      fixture.h.runtime.email,
                      "passwordFile",
                      fixture.h.runtime.passwordFile.toString(),
                      "providerPort",
                      fixture.server.getAddress().getPort())));
      while (!java.nio.file.Files.exists(stop)) Thread.sleep(250);
    } finally {
      fixture.close();
      java.nio.file.Files.deleteIfExists(metadata);
    }
  }

  @Test
  void insufficientSharedMoneyRollsBackReservationBeforeTransport() throws Exception {
    String t = p.task(), c = conversation(t, null);
    UUID owner =
        h.jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, h.runtime.email);
    java.time.LocalDate day = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
    var old =
        h.jdbc.query(
            "SELECT consumed FROM ai_money_days WHERE owner_id=? AND budget_day=? AND currency='USD'",
            (rs, n) -> rs.getBigDecimal(1),
            owner,
            day);
    java.math.BigDecimal previous = old.isEmpty() ? java.math.BigDecimal.ZERO : old.get(0);
    h.jdbc.update(
        "INSERT INTO ai_money_days(owner_id,budget_day,currency,consumed) VALUES(?,?,'USD',10) ON CONFLICT(owner_id,budget_day,currency) DO UPDATE SET consumed=10",
        owner,
        day);
    int before = calls.get();
    long jobs = h.jdbc.queryForObject("SELECT count(*) FROM tutor_turns", Long.class);
    try {
      assertThat(
              p.post(
                      "/tutor/conversations/" + c + "/stream",
                      Map.of("text", "success", "mode", "EXPLAIN", "confirmed", true),
                      UUID.randomUUID().toString())
                  .statusCode())
          .isEqualTo(422);
      assertThat(calls.get()).isEqualTo(before);
      assertThat(h.jdbc.queryForObject("SELECT count(*) FROM tutor_turns", Long.class))
          .isEqualTo(jobs);
    } finally {
      h.jdbc.update(
          "UPDATE ai_money_days SET consumed=? WHERE owner_id=? AND budget_day=? AND currency='USD'",
          previous,
          owner,
          day);
    }
  }

  @Test
  void explicitlyRecoversAbandonedReservationAndNeverStartsItsProviderLater() throws Exception {
    String t = p.task(), c = conversation(t, null);
    UUID owner =
        h.jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, h.runtime.email);
    var principal =
        new dev.scarletnexus.identity.SessionUser(
            new dev.scarletnexus.identity.UserAccount(
                owner, h.runtime.email, "test-only", "ACTIVE"));
    var auth =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            principal, null, principal.getAuthorities());
    var service = h.runtime.app.getBean(dev.scarletnexus.ai.TutorService.class);
    dev.scarletnexus.ai.TutorService.Prepared prepared;
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);
    try {
      prepared =
          service.prepare(
              UUID.fromString(c),
              new dev.scarletnexus.ai.TutorController.Chat("abandoned", "EXPLAIN", null, true),
              UUID.randomUUID().toString());
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    assertThat(
            h.c
                .write(
                    "POST",
                    "/tutor/turns/" + prepared.id() + "/recover",
                    Map.of("confirmed", false))
                .statusCode())
        .isEqualTo(422);
    assertThat(
            h.c
                .write(
                    "POST", "/tutor/turns/" + prepared.id() + "/recover", Map.of("confirmed", true))
                .statusCode())
        .isEqualTo(409);
    h.jdbc.update(
        "UPDATE tutor_turns SET created_at=now()-interval '6 minutes' WHERE id=?", prepared.id());
    var recovered =
        p.write("/tutor/turns/" + prepared.id() + "/recover", Map.of("confirmed", true));
    assertThat(recovered.path("state").asText()).isEqualTo("FAILED");
    assertThat(recovered.path("usageSource").asText()).isEqualTo("UNKNOWN");
    int before = calls.get();
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);
    try {
      service.stream(prepared, (name, data) -> {});
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    assertThat(calls.get()).isEqualTo(before);
  }

  String conversation(String task, String question) throws Exception {
    Map<String, Object> value = new HashMap<>();
    value.put("title", "Tutor fixture");
    value.put("questionId", question);
    return p.write("/tutor/tasks/" + task + "/conversations", value).path("id").asText();
  }

  JsonNode done(String body) throws Exception {
    for (String event : body.split("\n\n"))
      if (event.startsWith("event: done") || event.startsWith("event: snapshot"))
        return h.json.readTree(event.substring(event.indexOf("data: ") + 6));
    throw new AssertionError("No terminal SSE event: " + body);
  }

  @Test
  void trueSsePersistsAndBillsOnceWhileReplayOnlyReturnsSnapshot() throws Exception {
    String t = p.task(), c = conversation(t, null), key = UUID.randomUUID().toString();
    int before = calls.get();
    Map<String, Object> request = Map.of("text", "success", "mode", "SOCRATIC", "confirmed", true);
    var response = p.post("/tutor/conversations/" + c + "/stream", request, key);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("event: delta");
    var turn = done(response.body());
    assertThat(turn.path("state").asText()).isEqualTo("SUCCEEDED");
    assertThat(turn.path("usageSource").asText()).isEqualTo("PRICED_USAGE");
    assertThat(turn.path("chargedMoney").decimalValue()).isEqualByComparingTo("0.000036");
    var replay = p.post("/tutor/conversations/" + c + "/stream", request, key);
    assertThat(replay.body()).contains("event: snapshot").doesNotContain("event: delta");
    assertThat(calls.get()).isEqualTo(before + 1);
    assertThat(
            p.post(
                    "/tutor/conversations/" + c + "/stream",
                    Map.of("text", "different", "mode", "SOCRATIC", "confirmed", true),
                    key)
                .statusCode())
        .isEqualTo(409);
    assertThat(
            h.ok(h.c.get("/tutor/turns/" + turn.path("id").asText()))
                .path("assistantText")
                .asText())
        .contains("测试协议增量");
  }

  @Test
  void practiceRequiresSubmissionAndTruncationKeepsAttemptAndPartialOutput() throws Exception {
    String t = p.task(), q = p.question(t, "PROOF").path("id").asText(), c = conversation(t, q);
    int before = calls.get();
    assertThat(
            p.post(
                    "/tutor/conversations/" + c + "/stream",
                    Map.of("text", "fail", "mode", "EXPLAIN", "confirmed", true),
                    UUID.randomUUID().toString())
                .statusCode())
        .isEqualTo(422);
    assertThat(calls.get()).isEqualTo(before);
    String a = p.submit(t, q, "证明过程已独立提交", UUID.randomUUID().toString()).path("id").asText();
    var response =
        p.post(
            "/tutor/conversations/" + c + "/stream",
            Map.of("text", "fail", "mode", "EXPLAIN", "attemptId", a, "confirmed", true),
            UUID.randomUUID().toString());
    var turn = done(response.body());
    assertThat(turn.path("state").asText()).isEqualTo("FAILED");
    assertThat(turn.path("errorCode").asText()).isEqualTo("AI_INCOMPLETE_OUTPUT");
    assertThat(turn.path("assistantText").asText()).contains("测试协议增量");
    assertThat(turn.path("usageSource").asText()).isEqualTo("UNKNOWN");
    assertThat(
            h.ok(h.c.get("/practice/attempts/" + a))
                .path("attempt")
                .path("submittedContent")
                .asText())
        .isEqualTo("证明过程已独立提交");
    var retried =
        done(
            p.post(
                    "/tutor/conversations/" + c + "/stream",
                    Map.of("text", "success", "mode", "EXPLAIN", "attemptId", a, "confirmed", true),
                    UUID.randomUUID().toString())
                .body());
    assertThat(retried.path("state").asText()).isEqualTo("SUCCEEDED");
    assertThat(calls.get()).isEqualTo(before + 2);
  }

  @Test
  void timeoutKeepsQuestionAndUnknownBudgetWithoutAnAutomaticRetry() throws Exception {
    String t = p.task(), c = conversation(t, null);
    int before = calls.get();
    var turn =
        done(
            p.post(
                    "/tutor/conversations/" + c + "/stream",
                    Map.of("text", "timeout", "mode", "EXPLAIN", "confirmed", true),
                    UUID.randomUUID().toString())
                .body());
    assertThat(turn.path("state").asText()).isEqualTo("FAILED");
    assertThat(turn.path("errorCode").asText()).isEqualTo("AI_TIMEOUT");
    assertThat(turn.path("chargedMoney").decimalValue()).isEqualByComparingTo("1");
    assertThat(calls.get()).isEqualTo(before + 1);
  }
}
