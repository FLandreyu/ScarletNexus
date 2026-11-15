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
      container = new PostgreSQLContainer<>("postgres:17.11-alpine");
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

  @Test
  @Order(1)
  void migratesAnEmptyRealPostgresAndReportsDatabaseState() {
    assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("17.11");
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    var response = http.getForEntity("/api/v1/system/status", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("schemaVersion", "3")
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
