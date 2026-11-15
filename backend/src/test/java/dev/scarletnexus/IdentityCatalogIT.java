package dev.scarletnexus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "debug=false")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdentityCatalogIT {
  static final String EMAIL = "owner@example.test";
  static final String PASSWORD = "Integration-only-password-2026";
  static final ObjectMapper JSON = new ObjectMapper();
  static EmbeddedPostgres pg;
  static PostgreSQLContainer<?> container;
  static Path passwordFile;
  static String jdbcUrl, username, dbPassword;
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) throws Exception {
    if ("container".equals(System.getProperty("test.database"))) {
      container = new PostgreSQLContainer<>("postgres:17.11-alpine");
      container.start();
      jdbcUrl = container.getJdbcUrl();
      username = container.getUsername();
      dbPassword = container.getPassword();
    } else {
      pg = EmbeddedPostgres.builder().start();
      jdbcUrl = pg.getJdbcUrl("postgres", "postgres");
      username = "postgres";
      dbPassword = "";
    }
    passwordFile = Files.createTempFile("scarlet-admin-test-", ".txt");
    Files.writeString(passwordFile, PASSWORD);
    registry.add("spring.datasource.url", () -> jdbcUrl);
    registry.add("spring.datasource.username", () -> username);
    registry.add("spring.datasource.password", () -> dbPassword);
    registry.add("app.bootstrap.email", () -> EMAIL);
    registry.add("app.bootstrap.password-file", () -> passwordFile.toString());
  }

  @AfterAll
  static void close() throws Exception {
    if (pg != null) pg.close();
    if (container != null) container.stop();
    if (passwordFile != null) Files.deleteIfExists(passwordFile);
  }

  private Client client() {
    return new Client(port);
  }

  private Client owner() throws Exception {
    Client c = client();
    assertThat(
            c.write("POST", "/auth/login", Map.of("email", EMAIL, "password", PASSWORD))
                .statusCode())
        .isEqualTo(200);
    return c;
  }

  private static JsonNode json(HttpResponse<String> response) throws Exception {
    return JSON.readTree(response.body());
  }

  private static Map<String, Object> skill(String name, String status) {
    return Map.of(
        "name", name, "description", "A real integration test", "category", "工程", "status", status);
  }

  private static Map<String, Object> node(String title) {
    return Map.of("title", title, "description", "", "nodeType", "CONCEPT", "status", "ACTIVE");
  }

  private String createSkill(Client client, String title) throws Exception {
    var response = client.write("POST", "/skills", skill(title, "ACTIVE"));
    assertThat(response.statusCode()).isEqualTo(201);
    return json(response).get("id").asText();
  }

  private String createNode(Client client, String title) throws Exception {
    var response = client.write("POST", "/knowledge/nodes", node(title));
    assertThat(response.statusCode()).isEqualTo(201);
    return json(response).get("id").asText();
  }

  private void attach(Client client, String skill, String node, String parent, int order)
      throws Exception {
    var value = new HashMap<String, Object>();
    value.put("nodeId", node);
    value.put("parentNodeId", parent);
    value.put("displayOrder", order);
    assertThat(client.write("POST", "/skills/" + skill + "/nodes", value).statusCode())
        .isEqualTo(201);
  }

  private static Map<String, Object> relation(String source, String target) {
    return Map.of(
        "sourceNodeId", source, "targetNodeId", target, "relationType", "PREREQUISITE_OF");
  }

  @Test
  @Order(1)
  void bootstrapHasNoPlainPasswordAndDoesNotResetExistingAccount() throws Exception {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
    String hash =
        jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, EMAIL);
    assertThat(hash).startsWith("$2a$12$").doesNotContain(PASSWORD);
    assertThat(encoder.matches(PASSWORD, hash)).isTrue();
    try (var app =
        new SpringApplicationBuilder(ScarletNexusApplication.class)
            .run(
                "--server.port=0",
                "--debug=false",
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=" + username,
                "--spring.datasource.password=" + dbPassword,
                "--app.bootstrap.email=" + EMAIL,
                "--app.bootstrap.password-file=nonexistent-test-file")) {
      assertThat(
              app.getBean(JdbcTemplate.class)
                  .queryForObject("SELECT count(*) FROM users", Integer.class))
          .isEqualTo(1);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email=?", String.class, EMAIL))
        .isEqualTo(hash);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action='BOOTSTRAPPED'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @Order(2)
  void csrfLoginRotationLogoutAndGenericFailure() throws Exception {
    Client client = client();
    var csrf = client.get("/auth/csrf");
    String anonymousCsrf = json(csrf).get("token").asText();
    assertThat(csrf.headers().firstValue("Set-Cookie").orElseThrow())
        .contains("HttpOnly", "SameSite=Lax");
    String before = client.cookies.getCookieStore().getCookies().getFirst().getValue();
    assertThat(
            client
                .send("POST", "/auth/login", Map.of("email", EMAIL, "password", PASSWORD), null)
                .statusCode())
        .isEqualTo(403);
    var bad = client.write("POST", "/auth/login", Map.of("email", EMAIL, "password", "incorrect"));
    var absent =
        client.write(
            "POST",
            "/auth/login",
            Map.of("email", "missing@example.test", "password", "incorrect"));
    assertThat(bad.statusCode()).isEqualTo(401);
    assertThat(json(bad).get("message")).isEqualTo(json(absent).get("message"));
    assertThat(
            client
                .write("POST", "/auth/login", Map.of("email", EMAIL, "password", PASSWORD))
                .statusCode())
        .isEqualTo(200);
    assertThat(client.cookies.getCookieStore().getCookies().getFirst().getValue())
        .isNotEqualTo(before);
    assertThat(client.send("POST", "/skills", Map.of(), anonymousCsrf).statusCode()).isEqualTo(403);
    assertThat(json(client.get("/me")).get("email").asText()).isEqualTo(EMAIL);
    assertThat(client.get("/me").body()).doesNotContain("password");
    assertThat(client.write("POST", "/auth/logout", Map.of()).statusCode()).isEqualTo(204);
    assertThat(client.get("/me").statusCode()).isEqualTo(401);
  }

  @Test
  @Order(3)
  void persistsAuthenticatedSessionAcrossApplicationRestart() throws Exception {
    Client client = owner();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name=?", Integer.class, EMAIL))
        .isPositive();
    try (var app =
        new SpringApplicationBuilder(ScarletNexusApplication.class)
            .run(
                "--server.port=0",
                "--debug=false",
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=" + username,
                "--spring.datasource.password=" + dbPassword)) {
      int restartedPort = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
      client.port = restartedPort;
      assertThat(json(client.get("/me")).get("email").asText()).isEqualTo(EMAIL);
      assertThat(client.write("POST", "/auth/logout", Map.of()).statusCode()).isEqualTo(204);
    }
    client.port = port;
    assertThat(client.get("/me").statusCode()).isEqualTo(401);
  }

  @Test
  @Order(4)
  void isolatesUsersAndValidatesOwnedReferencesInDatabaseAndApi() throws Exception {
    Client alice = owner();
    String skill = createSkill(alice, "Java 后端");
    String node = createNode(alice, "Java SE");
    attach(alice, skill, node, null, 0);
    UUID bobId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users(id,email,password_hash) VALUES(?,?,?)",
        bobId,
        "second@example.test",
        encoder.encode(PASSWORD));
    Client bob = client();
    assertThat(
            bob.write(
                    "POST",
                    "/auth/login",
                    Map.of("email", "second@example.test", "password", PASSWORD))
                .statusCode())
        .isEqualTo(200);
    String foreign = createNode(bob, "Other private node");
    assertThat(bob.get("/skills/" + skill + "/graph").statusCode()).isEqualTo(404);
    assertThat(bob.write("PATCH", "/skills/" + skill, skill("changed", "ACTIVE")).statusCode())
        .isEqualTo(404);
    assertThat(alice.write("POST", "/knowledge/relations", relation(node, foreign)).statusCode())
        .isEqualTo(404);
    assertThat(json(bob.get("/skills")).get("total").asInt()).isZero();
    UUID aliceId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, EMAIL);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO skill_nodes(id,owner_id,skill_id,node_id) VALUES(?,?,?,?)",
                    UUID.randomUUID(),
                    aliceId,
                    UUID.fromString(skill),
                    UUID.fromString(foreign)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(alice.write("POST", "/skills", skill(" ", "ACTIVE")).statusCode()).isEqualTo(400);
  }

  @Test
  @Order(5)
  void sharesStableNodesWithoutSharingSkillOrderAndRejectsCategoryCycles() throws Exception {
    Client c = owner();
    String first = createSkill(c, "Tree one");
    String second = createSkill(c, "Tree two");
    String a = createNode(c, "A"), b = createNode(c, "B"), d = createNode(c, "C");
    attach(c, first, a, null, 0);
    attach(c, first, b, a, 1);
    attach(c, first, d, b, 2);
    attach(c, second, b, null, 7);
    var cycle =
        c.write(
            "PATCH",
            "/skills/" + first + "/nodes/" + a,
            Map.of("parentNodeId", d, "displayOrder", 0));
    assertThat(cycle.statusCode()).isEqualTo(422);
    assertThat(json(cycle).get("code").asText()).isEqualTo("CATEGORY_CYCLE");
    assertThat(c.write("DELETE", "/skills/" + first + "/nodes/" + a, Map.of()).statusCode())
        .isEqualTo(422);
    assertThat(
            c.write(
                    "PATCH",
                    "/skills/" + first + "/nodes/" + b,
                    Map.of("parentNodeId", a, "displayOrder", 20))
                .statusCode())
        .isEqualTo(200);
    assertThat(c.write("PATCH", "/knowledge/nodes/" + b, node("Renamed stable UUID")).statusCode())
        .isEqualTo(200);
    var graph = json(c.get("/skills/" + second + "/graph"));
    assertThat(graph.get("nodes").get(0).get("id").asText()).isEqualTo(b);
    assertThat(graph.get("nodes").get(0).get("displayOrder").asInt()).isEqualTo(7);
    assertThat(graph.get("nodes").get(0).get("title").asText()).isEqualTo("Renamed stable UUID");
    assertThat(c.write("DELETE", "/skills/" + first + "/nodes/" + d, Map.of()).statusCode())
        .isEqualTo(204);
    assertThat(c.get("/knowledge/nodes/" + d).statusCode()).isEqualTo(200);
  }

  @Test
  @Order(6)
  void rejectsDependencyCyclesAndSerializesConcurrentOppositeEdges() throws Exception {
    Client c = owner();
    String a = createNode(c, "DAG A"), b = createNode(c, "DAG B"), d = createNode(c, "DAG C");
    assertThat(c.write("POST", "/knowledge/relations", relation(a, b)).statusCode()).isEqualTo(201);
    assertThat(c.write("POST", "/knowledge/relations", relation(b, d)).statusCode()).isEqualTo(201);
    int before = jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class);
    var response = c.write("POST", "/knowledge/relations", relation(d, a));
    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(json(response).get("code").asText()).isEqualTo("DEPENDENCY_CYCLE");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class))
        .isEqualTo(before);
    String x = createNode(c, "Concurrent X"), y = createNode(c, "Concurrent Y");
    String token = json(c.get("/auth/csrf")).get("token").asText();
    var one =
        c.http.sendAsync(
            c.request("POST", "/knowledge/relations", relation(x, y), token),
            HttpResponse.BodyHandlers.ofString());
    var two =
        c.http.sendAsync(
            c.request("POST", "/knowledge/relations", relation(y, x), token),
            HttpResponse.BodyHandlers.ofString());
    assertThat(List.of(one.get().statusCode(), two.get().statusCode()))
        .containsExactlyInAnyOrder(201, 422);
  }

  @Test
  @Order(7)
  void searchesPaginatesAndArchivesWithoutDeletingRecords() throws Exception {
    Client c = owner();
    String id = createSkill(c, "Searchable Java%API");
    assertThat(json(c.get("/skills?q=%25&size=1")).get("total").asInt()).isEqualTo(1);
    assertThat(c.get("/skills?size=101").statusCode()).isEqualTo(422);
    assertThat(
            c.write("PATCH", "/skills/" + id, skill("Searchable Java%API", "ARCHIVED"))
                .statusCode())
        .isEqualTo(200);
    assertThat(json(c.get("/skills?q=Searchable")).get("total").asInt()).isZero();
    assertThat(json(c.get("/skills?q=Searchable&status=ARCHIVED")).get("total").asInt())
        .isEqualTo(1);
    assertThat(
            c.write("PATCH", "/skills/" + id, skill("Searchable Java%API", "ACTIVE")).statusCode())
        .isEqualTo(200);
  }

  @Test
  @Order(8)
  void draftsValidateConflictPublishIdempotentlyAndRollbackOnFailure() throws Exception {
    Client c = owner();
    String skill = createSkill(c, "Draft acceptance");
    assertThat(json(c.get("/ai/generation")).get("enabled").asBoolean()).isFalse();
    String generationCsrf = json(c.get("/auth/csrf")).get("token").asText();
    var disabledGeneration =
        c.http.send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://127.0.0.1:"
                            + port
                            + "/api/v1/skills/"
                            + skill
                            + "/graph/generation"))
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", generationCsrf)
                .header("Idempotency-Key", "disabled-generation")
                .POST(HttpRequest.BodyPublishers.ofString("{\"topic\":\"test topic\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(disabledGeneration.statusCode()).isEqualTo(503);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ai_generation_jobs WHERE request_key='disabled-generation'",
                Integer.class))
        .isZero();
    String a = createNode(c, "Original"), b = createNode(c, "Second");
    attach(c, skill, a, null, 0);
    attach(c, skill, b, a, 1);
    var draft = json(c.write("POST", "/skills/" + skill + "/graph/drafts", Map.of()));
    String id = draft.get("id").asText();
    UUID jobId = UUID.randomUUID();
    UUID ownerId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, EMAIL);
    jdbc.update(
        "INSERT INTO ai_daily_budgets(owner_id,budget_day,reserved_tokens) VALUES(?,CURRENT_DATE,100)",
        ownerId);
    jdbc.update(
        "INSERT INTO ai_generation_jobs(id,owner_id,skill_id,request_key,input_hash,budget_day,reserved_tokens,state,draft_id) VALUES(?,?,?,'recover-test',?,CURRENT_DATE,100,'RUNNING',?)",
        jobId,
        ownerId,
        UUID.fromString(skill),
        "0".repeat(64),
        UUID.fromString(id));
    assertThat(c.get("/ai/jobs").statusCode()).isEqualTo(200);
    assertThat(json(c.get("/ai/jobs/" + jobId + "/cost")).get("cost").isNull()).isTrue();
    assertThat(c.get("/ai/jobs/" + UUID.randomUUID() + "/cost").statusCode()).isEqualTo(404);
    assertThat(json(c.get("/ai/jobs/" + jobId)).get("recoverable").asBoolean()).isFalse();
    assertThat(
            c.write("POST", "/ai/jobs/" + jobId + "/recover", Map.of("confirmed", false))
                .statusCode())
        .isEqualTo(422);
    assertThat(
            c.write("POST", "/ai/jobs/" + jobId + "/recover", Map.of("confirmed", true))
                .statusCode())
        .isEqualTo(409);
    jdbc.update(
        "UPDATE ai_generation_jobs SET created_at=now()-interval '6 minutes' WHERE id=?", jobId);
    var recovered =
        json(c.write("POST", "/ai/jobs/" + jobId + "/recover", Map.of("confirmed", true)));
    assertThat(recovered.get("state").asText()).isEqualTo("FAILED");
    assertThat(recovered.get("chargedTokens").asInt()).isEqualTo(100);
    assertThat(
            c.write("POST", "/ai/jobs/" + jobId + "/recover", Map.of("confirmed", true))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT consumed_tokens FROM ai_daily_budgets WHERE owner_id=?",
                Long.class,
                ownerId))
        .isEqualTo(100);
    assertThat(c.get("/ai/jobs/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
    assertThat(c.get("/ai/jobs?size=101").statusCode()).isEqualTo(422);
    var document =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            JSON.readTree(draft.get("document").asText());
    ((com.fasterxml.jackson.databind.node.ObjectNode) document.get("nodes").get(0))
        .put("title", "Draft rename");
    assertThat(
            c.write("PATCH", "/graph/drafts/" + id, Map.of("revision", 0, "document", document))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            c.write("PATCH", "/graph/drafts/" + id, Map.of("revision", 0, "document", document))
                .statusCode())
        .isEqualTo(409);
    assertThat(json(c.get("/knowledge/nodes/" + a)).get("title").asText()).isEqualTo("Original");
    assertThat(
            json(c.write("POST", "/graph/drafts/" + id + "/validate", Map.of()))
                .get("valid")
                .asBoolean())
        .isTrue();
    var request =
        c.request(
            "POST",
            "/graph/drafts/" + id + "/publish",
            Map.of("revision", 1, "confirmed", true),
            json(c.get("/auth/csrf")).get("token").asText());
    var publish =
        java.net.http.HttpRequest.newBuilder(request.uri())
            .headers(
                "Content-Type",
                "application/json",
                "X-CSRF-TOKEN",
                json(c.get("/auth/csrf")).get("token").asText(),
                "Idempotency-Key",
                "publish-test-1")
            .POST(
                java.net.http.HttpRequest.BodyPublishers.ofString(
                    "{\"revision\":1,\"confirmed\":true}"))
            .build();
    var result = c.http.send(publish, HttpResponse.BodyHandlers.ofString());
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(json(c.http.send(publish, HttpResponse.BodyHandlers.ofString())).get("id"))
        .isEqualTo(json(result).get("id"));
    assertThat(json(c.get("/skills/" + skill + "/graph/versions")).size()).isEqualTo(1);
    assertThatThrownBy(() -> jdbc.update("UPDATE knowledge_graph_versions SET version_number=2"))
        .isInstanceOf(DataIntegrityViolationException.class);
    var stale = json(c.write("POST", "/skills/" + skill + "/graph/drafts", Map.of()));
    assertThat(c.write("PATCH", "/knowledge/nodes/" + a, node("Live edit")).statusCode())
        .isEqualTo(409);
    c.write("PATCH", "/skills/" + skill, skill("Changed metadata", "ACTIVE"));
    assertThat(
            json(c.write(
                    "POST", "/graph/drafts/" + stale.get("id").asText() + "/validate", Map.of()))
                .get("sourceChanged")
                .asBoolean())
        .isTrue();
    var fresh = json(c.write("POST", "/skills/" + skill + "/graph/drafts", Map.of()));
    String freshId = fresh.get("id").asText();
    var changed =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            JSON.readTree(fresh.get("document").asText());
    ((com.fasterxml.jackson.databind.node.ObjectNode) changed.get("nodes").get(0))
        .put("title", "Must roll back");
    c.write("PATCH", "/graph/drafts/" + freshId, Map.of("revision", 0, "document", changed));
    jdbc.execute(
        "CREATE FUNCTION fail_publish_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected failure' USING ERRCODE='23514'; END; $$");
    jdbc.execute(
        "CREATE TRIGGER fail_publish_test BEFORE INSERT ON knowledge_graph_versions FOR EACH ROW EXECUTE FUNCTION fail_publish_test()");
    try {
      var fail =
          java.net.http.HttpRequest.newBuilder(
                  URI.create(
                      "http://127.0.0.1:" + port + "/api/v1/graph/drafts/" + freshId + "/publish"))
              .headers(
                  "Content-Type",
                  "application/json",
                  "X-CSRF-TOKEN",
                  json(c.get("/auth/csrf")).get("token").asText(),
                  "Idempotency-Key",
                  "rollback-test")
              .POST(
                  java.net.http.HttpRequest.BodyPublishers.ofString(
                      "{\"revision\":1,\"confirmed\":true}"))
              .build();
      assertThat(c.http.send(fail, HttpResponse.BodyHandlers.ofString()).statusCode())
          .isEqualTo(409);
      assertThat(json(c.get("/knowledge/nodes/" + a)).get("title").asText())
          .isEqualTo("Draft rename");
      assertThat(json(c.get("/graph/drafts/" + freshId)).get("status").asText())
          .isEqualTo("EDITING");
      assertThat(json(c.get("/skills/" + skill + "/graph/versions")).size()).isEqualTo(1);
    } finally {
      jdbc.execute("DROP TRIGGER fail_publish_test ON knowledge_graph_versions");
      jdbc.execute("DROP FUNCTION fail_publish_test()");
    }
    String otherSkill = createSkill(c, "Shared published skill");
    attach(c, otherSkill, a, null, 0);
    var other = json(c.write("POST", "/skills/" + otherSkill + "/graph/drafts", Map.of()));
    var otherRequest =
        java.net.http.HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + "/api/v1/graph/drafts/"
                        + other.get("id").asText()
                        + "/publish"))
            .headers(
                "Content-Type",
                "application/json",
                "X-CSRF-TOKEN",
                json(c.get("/auth/csrf")).get("token").asText(),
                "Idempotency-Key",
                "shared-unchanged")
            .POST(
                java.net.http.HttpRequest.BodyPublishers.ofString(
                    "{\"revision\":0,\"confirmed\":true}"))
            .build();
    assertThat(c.http.send(otherRequest, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(200);
    var protectedDraft = json(c.write("POST", "/skills/" + skill + "/graph/drafts", Map.of()));
    var forbidden =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            JSON.readTree(protectedDraft.get("document").asText());
    ((com.fasterxml.jackson.databind.node.ObjectNode) forbidden.get("nodes").get(0))
        .put("title", "Forbidden shared change");
    c.write(
        "PATCH",
        "/graph/drafts/" + protectedDraft.get("id").asText(),
        Map.of("revision", 0, "document", forbidden));
    assertThat(
            json(c.write(
                    "POST",
                    "/graph/drafts/" + protectedDraft.get("id").asText() + "/validate",
                    Map.of()))
                .get("valid")
                .asBoolean())
        .isFalse();
    String jointId = protectedDraft.get("id").asText();
    assertThat(
            json(c.write(
                    "POST",
                    "/graph/drafts/" + jointId + "/validate",
                    Map.of("includeAffectedSkills", true)))
                .get("valid")
                .asBoolean())
        .isTrue();
    var jointRequest =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:" + port + "/api/v1/graph/drafts/" + jointId + "/publish"))
            .headers(
                "Content-Type",
                "application/json",
                "X-CSRF-TOKEN",
                json(c.get("/auth/csrf")).get("token").asText(),
                "Idempotency-Key",
                "joint-acceptance")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    JSON.writeValueAsString(
                        Map.of("revision", 1, "confirmed", true, "includeAffectedSkills", true))))
            .build();
    int beforeJoint =
        jdbc.queryForObject("SELECT count(*) FROM knowledge_graph_versions", Integer.class);
    jdbc.execute(
        "CREATE FUNCTION fail_joint_version() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.skill_id='"
            + otherSkill
            + "'::uuid THEN RAISE EXCEPTION 'injected joint publish failure'; END IF; RETURN NEW; END $$");
    jdbc.execute(
        "CREATE TRIGGER fail_joint_version BEFORE INSERT ON knowledge_graph_versions FOR EACH ROW EXECUTE FUNCTION fail_joint_version()");
    try {
      assertThat(c.http.send(jointRequest, HttpResponse.BodyHandlers.ofString()).statusCode())
          .isEqualTo(503);
      assertThat(
              jdbc.queryForObject("SELECT count(*) FROM knowledge_graph_versions", Integer.class))
          .isEqualTo(beforeJoint);
      assertThat(json(c.get("/graph/drafts/" + jointId)).get("status").asText())
          .isEqualTo("EDITING");
    } finally {
      jdbc.execute("DROP TRIGGER fail_joint_version ON knowledge_graph_versions");
      jdbc.execute("DROP FUNCTION fail_joint_version()");
    }
    assertThat(c.http.send(jointRequest, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(200);
    assertThat(json(c.get("/skills/" + otherSkill + "/graph/versions")).size()).isEqualTo(2);
    assertThat(c.get("/skills/" + otherSkill + "/graph").body())
        .contains("Forbidden shared change");
    assertThat(c.http.send(jointRequest, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(200);
    assertThat(json(c.get("/skills/" + otherSkill + "/graph/versions")).size()).isEqualTo(2);
  }

  @Test
  @Order(90)
  void rateLimitsRepeatedLoginAttemptsWithoutLeakingCredentials() throws Exception {
    Client c = client();
    HttpResponse<String> response = null;
    for (int i = 0; i < 12; i++) {
      response = c.write("POST", "/auth/login", Map.of("email", EMAIL, "password", "incorrect"));
      if (response.statusCode() == 429) break;
    }
    assertThat(response.statusCode()).isEqualTo(429);
    assertThat(response.headers().firstValue("Retry-After")).isPresent();
    assertThat(response.body()).doesNotContain(PASSWORD, "password_hash");
  }

  static final class Client {
    int port;
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

    Client(int port) {
      this.port = port;
    }

    HttpRequest request(String method, String path, Object body, String csrf) throws Exception {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
              .header("Accept", "application/json");
      if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
      if (body != null)
        request
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
      else request.method(method, HttpRequest.BodyPublishers.noBody());
      return request.build();
    }

    HttpResponse<String> send(String method, String path, Object body, String csrf)
        throws Exception {
      return http.send(request(method, path, body, csrf), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path) throws Exception {
      return send("GET", path, null, null);
    }

    HttpResponse<String> write(String method, String path, Object body) throws Exception {
      String csrf = json(get("/auth/csrf")).get("token").asText();
      return send(method, path, body, csrf);
    }
  }
}
