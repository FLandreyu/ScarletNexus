package dev.scarletnexus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "debug=false")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemIT {
  private static EmbeddedPostgres embedded;
  private static PostgreSQLContainer<?> container;
  private static String url;
  private static String username;
  private static String password;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) throws Exception {
    if ("container".equals(System.getProperty("test.database"))) {
      container = new PostgreSQLContainer<>(TestImages.POSTGRES);
      container.start();
      url = container.getJdbcUrl();
      username = container.getUsername();
      password = container.getPassword();
    } else {
      embedded = EmbeddedPostgres.builder().start();
      url = embedded.getJdbcUrl("postgres", "postgres");
      username = "postgres";
      password = "";
    }
    registry.add("spring.datasource.url", () -> url);
    registry.add("spring.datasource.username", () -> username);
    registry.add("spring.datasource.password", () -> password);
  }

  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;
  @Autowired dev.scarletnexus.ai.GenerationJobs jobs;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
  @Autowired dev.scarletnexus.ai.MoneyLedger money;
  @Autowired dev.scarletnexus.ai.MoneyLedger.Store costs;
  @Autowired org.springframework.transaction.PlatformTransactionManager manager;
  @Autowired org.springframework.context.ApplicationContext context;

  @Test
  @Order(5)
  void persistsGenerationAndSettlesBudgetWithoutPublishing() throws Exception {
    UUID owner = UUID.randomUUID(), skill = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users(id,email,password_hash) VALUES(?,?,?)",
        owner,
        owner + "@test.invalid",
        "test-only-password-hash-placeholder");
    jdbc.update("INSERT INTO skills(id,owner_id,name) VALUES(?,?,?)", skill, owner, "AI test");
    var principal =
        new dev.scarletnexus.identity.SessionUser(
            new dev.scarletnexus.identity.UserAccount(
                owner, owner + "@test.invalid", "test-only", "ACTIVE"));
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    try {
      var calls = new java.util.concurrent.atomic.AtomicInteger();
      dev.scarletnexus.ai.AIProvider fake =
          (instructions, input, schema) -> {
            calls.incrementAndGet();
            assertThat(
                    org.springframework.transaction.support.TransactionSynchronizationManager
                        .isActualTransactionActive())
                .isFalse();
            return new dev.scarletnexus.ai.AIProvider.Result(
                json.createObjectNode()
                    .put("schemaVersion", 1)
                    .set("nodes", json.createArrayNode()),
                new dev.scarletnexus.ai.AIProvider.Usage(10L, 5L, "PROVIDER"));
          };
      // Invalid graph is retained as a failed task, never materialized as a candidate.
      var failed = jobs.generate(skill, "invalid", "topic", 100, 200, fake);
      assertThat(failed.state()).isEqualTo("FAILED");
      assertThat(failed.chargedTokens()).isEqualTo(15);
      assertThat(jobs.generate(skill, "invalid", "topic", 100, 200, fake).id())
          .isEqualTo(failed.id());
      assertThat(calls.get()).isEqualTo(1);
      var valid = json.readTree("{\"schemaVersion\":1,\"nodes\":[],\"relations\":[]}");
      var success =
          jobs.generate(
              skill,
              "valid",
              "topic",
              100,
              200,
              (a, b, c) ->
                  new dev.scarletnexus.ai.AIProvider.Result(
                      valid, new dev.scarletnexus.ai.AIProvider.Usage(20L, 10L, "PROVIDER")));
      assertThat(success.state()).isEqualTo("SUCCEEDED");
      assertThat(
              jdbc.queryForObject("SELECT count(*) FROM knowledge_graph_versions", Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT revision FROM graph_drafts WHERE id=?", Integer.class, success.draftId()))
          .isEqualTo(1);
      var disabled =
          jobs.generate(
              skill,
              "disabled",
              "topic",
              100,
              200,
              (a, b, c) -> {
                throw new dev.scarletnexus.ai.AIProvider.Failure("AI_NOT_CONFIGURED", false);
              });
      assertThat(disabled.chargedTokens()).isZero();
      var uncertain =
          jobs.generate(
              skill,
              "timeout",
              "topic",
              100,
              200,
              (a, b, c) -> {
                throw new dev.scarletnexus.ai.AIProvider.Failure("AI_TIMEOUT", true);
              });
      assertThat(uncertain.chargedTokens()).isEqualTo(100);
      assertThatThrownBy(() -> jobs.generate(skill, "over", "topic", 100, 200, fake))
          .isInstanceOf(dev.scarletnexus.common.DomainFailure.class);
      assertThat(
              jdbc.queryForObject(
                  "SELECT reserved_tokens FROM ai_daily_budgets WHERE owner_id=?",
                  Long.class,
                  owner))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT consumed_tokens FROM ai_daily_budgets WHERE owner_id=?",
                  Long.class,
                  owner))
          .isEqualTo(145);
      try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
        for (int i = 0; i < 2; i++) {
          String raceKey = "race-" + i;
          futures.add(
              pool.submit(
                  () -> {
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .setAuthentication(
                            new org.springframework.security.authentication
                                .UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
                    try {
                      barrier.await();
                      jobs.generate(
                          skill,
                          raceKey,
                          "topic",
                          100,
                          245,
                          (a, b, c) -> {
                            throw new dev.scarletnexus.ai.AIProvider.Failure("AI_TIMEOUT", true);
                          });
                      return true;
                    } catch (dev.scarletnexus.common.DomainFailure e) {
                      return false;
                    } finally {
                      org.springframework.security.core.context.SecurityContextHolder
                          .clearContext();
                    }
                  }));
        }
        int accepted = 0;
        for (var f : futures) if (f.get(10, java.util.concurrent.TimeUnit.SECONDS)) accepted++;
        assertThat(accepted).isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                    "SELECT consumed_tokens FROM ai_daily_budgets WHERE owner_id=?",
                    Long.class,
                    owner))
            .isEqualTo(245);
      }
      var late =
          jobs.generate(
              skill,
              "late-result",
              "topic",
              100,
              1000,
              (a, b, c) -> {
                var running = jobs.get("late-result");
                jdbc.update(
                    "UPDATE ai_generation_jobs SET created_at=now()-interval '6 minutes' WHERE id=?",
                    running.id());
                assertThat(jobs.recover(running.id(), true).state()).isEqualTo("FAILED");
                return new dev.scarletnexus.ai.AIProvider.Result(
                    valid, new dev.scarletnexus.ai.AIProvider.Usage(1L, 1L, "PROVIDER"));
              });
      assertThat(late.state()).isEqualTo("FAILED");
      assertThat(late.chargedTokens()).isEqualTo(100);
      var moneyTx = new org.springframework.transaction.support.TransactionTemplate(manager);
      var price =
          new dev.scarletnexus.ai.MoneyLedger.Price(
              "USD",
              "local-test",
              "test-model",
              "test-v1",
              java.time.LocalDate.of(2026, 1, 1),
              new java.math.BigDecimal("2"),
              new java.math.BigDecimal("4"),
              new java.math.BigDecimal("0.01"),
              new java.math.BigDecimal("0.02"));
      moneyTx.executeWithoutResult(
          s -> {
            money.reserve(owner, success.id(), success.budgetDay(), price);
            money.settle(
                owner,
                success.id(),
                new dev.scarletnexus.ai.AIProvider.Usage(1000L, 500L, "PROVIDER"),
                false);
            money.settle(owner, success.id(), null, false);
          });
      assertThat(costs.cost(owner, success.id()).charged()).isEqualByComparingTo("0.004");
      assertThat(costs.cost(owner, success.id()).priceVersion()).isEqualTo("test-v1");
      moneyTx.executeWithoutResult(
          s -> {
            money.reserve(owner, failed.id(), failed.budgetDay(), price);
            money.settle(owner, failed.id(), null, false);
          });
      assertThat(costs.cost(owner, failed.id()).source()).isEqualTo("UNKNOWN");
      assertThatThrownBy(
              () ->
                  moneyTx.executeWithoutResult(
                      s -> money.reserve(owner, late.id(), late.budgetDay(), price)))
          .isInstanceOf(dev.scarletnexus.common.DomainFailure.class);
      assertThat(costs.cost(owner, late.id())).isNull();
      assertThat(costs.cost(UUID.randomUUID(), success.id())).isNull();
      var policyEnv =
          new org.springframework.mock.env.MockEnvironment()
              .withProperty("app.ai.budget.enabled", "true")
              .withProperty("app.ai.model", "test-model")
              .withProperty("app.ai.budget.currency", "USD")
              .withProperty("app.ai.budget.provider-label", "local-test")
              .withProperty("app.ai.budget.price-version", "test-v2")
              .withProperty("app.ai.budget.price-date", "2026-01-02")
              .withProperty("app.ai.budget.input-per-million", "2")
              .withProperty("app.ai.budget.output-per-million", "4")
              .withProperty("app.ai.budget.per-job-reserve", "0.01")
              .withProperty("app.ai.budget.daily-limit", "0.02");
      var policyMoney =
          new dev.scarletnexus.ai.MoneyLedger(
              costs, policyEnv, context.getBean(dev.scarletnexus.skill.CatalogMapper.class));
      var fundedJobs =
          new dev.scarletnexus.ai.GenerationJobs(
              context.getBean(dev.scarletnexus.ai.GenerationJobs.Store.class),
              context.getBean(dev.scarletnexus.skill.CatalogMapper.class),
              context.getBean(dev.scarletnexus.knowledge.DraftService.class),
              context.getBean(dev.scarletnexus.knowledge.GraphValidator.class),
              json,
              context.getBean(dev.scarletnexus.common.AuditLog.class),
              manager,
              policyMoney);
      int draftCount = jdbc.queryForObject("SELECT count(*) FROM graph_drafts", Integer.class);
      var paidCalls = new java.util.concurrent.atomic.AtomicInteger();
      assertThatThrownBy(
              () ->
                  fundedJobs.generate(
                      skill,
                      "money-rejected",
                      "topic",
                      100,
                      1000,
                      (a, b, c) -> {
                        paidCalls.incrementAndGet();
                        return null;
                      }))
          .isInstanceOf(dev.scarletnexus.common.DomainFailure.class);
      assertThat(paidCalls.get()).isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM graph_drafts", Integer.class))
          .isEqualTo(draftCount);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM ai_generation_jobs WHERE request_key='money-rejected'",
                  Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT revision FROM graph_drafts WHERE id=?", Integer.class, late.draftId()))
          .isZero();
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
  }

  @Test
  @Order(1)
  void migratesAnEmptyRealPostgresAndReportsDatabaseState() {
    String expectedVersion =
        "container".equals(System.getProperty("test.database")) ? "17.12" : "17.11";
    assertThat(jdbc.queryForObject("SHOW server_version", String.class))
        .startsWith(expectedVersion);
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    var response = http.getForEntity("/api/v1/system/status", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("schemaVersion", "12")
        .containsEntry("database", "UP");
    assertThat(response.getBody()).containsEntry("aiConfigured", false);
    assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotBlank();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    assertThat(http.getForEntity("/actuator/health/readiness", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(http.getForEntity("/actuator/health", Map.class).getBody())
        .containsEntry("status", "UP")
        .doesNotContainKeys("components", "details");
  }

  @Test
  @Order(2)
  void protectsPrivateRoutesAndCsrfWithCorrelatedErrors() {
    var headers = new HttpHeaders();
    headers.set("X-Request-ID", "p0-security-test");
    var unauthorized =
        http.exchange("/api/v1/skills", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unauthorized.getBody())
        .containsEntry("code", "UNAUTHENTICATED")
        .containsEntry("requestId", "p0-security-test")
        .containsKey("details");
    assertThat(unauthorized.getHeaders().getFirst("X-Request-ID")).isEqualTo("p0-security-test");
    var forbidden = http.postForEntity("/api/v1/skills", Map.of("name", "test"), Map.class);
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(forbidden.getBody()).containsEntry("code", "FORBIDDEN");
    assertThat(
            http.withBasicAuth("user", "password")
                .getForEntity("/api/v1/skills", Map.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @Order(3)
  void enforcesUserAndAuditConstraintsInPostgres() {
    UUID user = UUID.randomUUID();
    String email = user + "@example.test";
    jdbc.update(
        "INSERT INTO users(id,email,password_hash) VALUES (?,?,?)",
        user,
        email,
        "test-only-hash-value-at-least-20");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO users(id,email,password_hash) VALUES (?,?,?)",
                    UUID.randomUUID(),
                    email,
                    "test-only-hash-value-at-least-20"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO users(id,email,password_hash) VALUES (?,?,?)",
                    UUID.randomUUID(),
                    "UPPER@example.test",
                    "test-only-hash-value-at-least-20"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("UPDATE users SET status='UNKNOWN' WHERE id=?", user))
        .isInstanceOf(DataIntegrityViolationException.class);
    UUID event = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO audit_events(id,user_id,entity_type,entity_id,action,request_id,after_json) VALUES (?,?,'USER',?,'CREATED','integration-test','{}')",
        event,
        user,
        user);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE audit_events SET action='CHANGED' WHERE id=?", event))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events WHERE id=?", event))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id=?", user))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO audit_events(id,entity_type,entity_id,action,request_id,after_json) VALUES (?,'USER',?,'BAD','integration-test','[]')",
                    UUID.randomUUID(),
                    user))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @Order(4)
  void retainsDataAcrossApplicationRestartAndExposesActualOpenApi() {
    int before = jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class);
    assertThat(before).isGreaterThan(0);
    String[] arguments = {
      "--server.port=0",
      "--spring.datasource.url=" + url,
      "--spring.datasource.username=" + username,
      "--spring.datasource.password=" + password,
      "--debug=false"
    };
    try (var restarted =
        new SpringApplicationBuilder(ScarletNexusApplication.class).run(arguments)) {
      assertThat(
              restarted
                  .getBean(JdbcTemplate.class)
                  .queryForObject("SELECT count(*) FROM audit_events", Integer.class))
          .isEqualTo(before);
      int port = ((ServletWebServerApplicationContext) restarted).getWebServer().getPort();
      assertThat(
              new TestRestTemplate()
                  .getForEntity("http://127.0.0.1:" + port + "/api/v1/system/status", Map.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
    }
    try (var afterRestart =
        new SpringApplicationBuilder(ScarletNexusApplication.class).run(arguments)) {
      assertThat(
              afterRestart
                  .getBean(JdbcTemplate.class)
                  .queryForObject("SELECT count(*) FROM audit_events", Integer.class))
          .isEqualTo(before);
    }
    String spec = http.getForObject("/v3/api-docs", String.class);
    assertThat(spec).contains("/api/v1/system/status", "/api/v1/skills", "/api/v1/auth/login");
  }

  @Test
  @Order(100)
  void reportsDatabaseOutageWithoutLeakingConfiguration() throws Exception {
    stopDatabase();
    var response = http.getForEntity("/api/v1/system/status", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).containsEntry("code", "DATABASE_UNAVAILABLE");
    assertThat(response.getBody().toString()).doesNotContain("jdbc:", "password", "SQLException");
    assertThat(http.getForEntity("/actuator/health/readiness", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(http.getForEntity("/actuator/health/liveness", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @AfterAll
  static void stopDatabase() throws Exception {
    if (embedded != null) {
      embedded.close();
      embedded = null;
    }
    if (container != null) {
      container.stop();
      container = null;
    }
  }
}
