package dev.scarletnexus;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransferIT {
  LearningIT h;
  PracticeIT p;

  @BeforeAll
  void start() throws Exception {
    h = new LearningIT();
    h.start();
    p = new PracticeIT();
    p.h = h;
  }

  @AfterAll
  void stop() throws Exception {
    h.stop();
  }

  String key() {
    return UUID.randomUUID().toString();
  }

  JsonNode get(String path) throws Exception {
    return h.ok(h.c.get(path));
  }

  JsonNode preview(JsonNode doc) throws Exception {
    return h.ok(h.c.write("POST", "/data/catalog/imports", Map.of("documentText", doc.toString())));
  }

  Map<String, Object> confirmation(JsonNode preview, boolean confirmed) {
    return Map.of(
        "revision",
        preview.path("revision").asInt(),
        "sourceHash",
        preview.path("sourceHash").asText(),
        "confirmed",
        confirmed);
  }

  String path(JsonNode preview) {
    return "/data/catalog/imports/" + preview.path("id").asText() + "/confirm";
  }

  @Test
  void previewDoesNotWriteCatalogAndConfirmCreatesSharedNodesWithoutOverwriting() throws Exception {
    var a = h.fixture();
    var b = h.fixture();
    h.ok(
        h.c.write(
            "POST",
            "/skills/" + b.skill() + "/nodes",
            Map.of("nodeId", a.node(), "displayOrder", 1)));
    h.task(a);
    var doc = get("/data/catalog");
    int skills = h.jdbc.queryForObject("SELECT count(*) FROM skills", Integer.class),
        nodes = h.jdbc.queryForObject("SELECT count(*) FROM knowledge_nodes", Integer.class);
    var v = preview(doc);
    assertThat(h.jdbc.queryForObject("SELECT count(*) FROM skills", Integer.class))
        .isEqualTo(skills);
    assertThat(p.post(path(v), confirmation(v, false), key()).statusCode()).isEqualTo(422);
    String key = key();
    var result = h.ok(p.post(path(v), confirmation(v, true), key));
    assertThat(h.ok(p.post(path(v), confirmation(v, true), key))).isEqualTo(result);
    String newA = result.path("skillIds").path(a.skill()).asText(),
        newB = result.path("skillIds").path(b.skill()).asText(),
        newNode = result.path("nodeIds").path(a.node()).asText();
    assertThat(newA).isNotEqualTo(a.skill());
    assertThat(newNode).isNotEqualTo(a.node());
    assertThat(get("/skills/" + newA + "/graph").path("nodes").findValuesAsText("id"))
        .contains(newNode);
    assertThat(get("/skills/" + newB + "/graph").path("nodes").findValuesAsText("id"))
        .contains(newNode);
    assertThat(get("/skills/" + a.skill() + "/graph").path("nodes").findValuesAsText("id"))
        .contains(a.node());
    assertThat(h.jdbc.queryForObject("SELECT count(*) FROM knowledge_nodes", Integer.class))
        .isEqualTo(nodes + doc.path("nodes").size());
    assertThat(p.post(path(v), confirmation(v, true), key()).statusCode()).isEqualTo(409);
    assertThatThrownBy(
            () ->
                h.jdbc.update(
                    "UPDATE catalog_imports SET document_json='{}'::jsonb WHERE id=?",
                    UUID.fromString(v.path("id").asText())))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void malformedUnknownFieldsCyclesAndTamperingNeverWriteCatalog() throws Exception {
    var f = h.fixture();
    var doc = (ObjectNode) get("/data/catalog?skillId=" + f.skill());
    var bad = doc.deepCopy();
    bad.put("owner_id", key());
    assertThat(
            h.c
                .write("POST", "/data/catalog/imports", Map.of("documentText", bad.toString()))
                .statusCode())
        .isEqualTo(422);
    bad = doc.deepCopy();
    ((ObjectNode) bad.path("skills").get(0)).put("name", 123);
    assertThat(
            h.c
                .write("POST", "/data/catalog/imports", Map.of("documentText", bad.toString()))
                .statusCode())
        .isEqualTo(422);
    bad = doc.deepCopy();
    ((ObjectNode) bad.path("positions").get(0)).put("parentNodeId", f.node());
    assertThat(
            h.c
                .write("POST", "/data/catalog/imports", Map.of("documentText", bad.toString()))
                .statusCode())
        .isEqualTo(422);
    assertThat(
            h.c
                .write(
                    "POST",
                    "/data/catalog/imports",
                    Map.of("documentText", "{\"format\":\"bad\",\"format\":\"other\"}"))
                .statusCode())
        .isEqualTo(422);
    var v = preview(doc);
    assertThat(
            p.post(
                    path(v),
                    Map.of("revision", 0, "sourceHash", "0".repeat(64), "confirmed", true),
                    key())
                .statusCode())
        .isEqualTo(409);
    assertThat(
            p.post(
                    path(v),
                    Map.of(
                        "revision",
                        1,
                        "sourceHash",
                        v.path("sourceHash").asText(),
                        "confirmed",
                        true),
                    key())
                .statusCode())
        .isEqualTo(409);
  }

  @Test
  void failureRollsBackNewEntitiesAndLeavesPreviewRetryable() throws Exception {
    var f = h.fixture();
    var v = preview(get("/data/catalog?skillId=" + f.skill()));
    int count = h.jdbc.queryForObject("SELECT count(*) FROM skills", Integer.class);
    h.jdbc.execute(
        "CREATE FUNCTION fail_import_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected import failure'; END $$");
    h.jdbc.execute(
        "CREATE TRIGGER fail_import_test BEFORE INSERT ON skill_nodes FOR EACH ROW EXECUTE FUNCTION fail_import_test()");
    String key = key();
    try {
      assertThat(p.post(path(v), confirmation(v, true), key).statusCode()).isEqualTo(503);
    } finally {
      h.jdbc.execute("DROP TRIGGER fail_import_test ON skill_nodes");
      h.jdbc.execute("DROP FUNCTION fail_import_test()");
    }
    assertThat(h.jdbc.queryForObject("SELECT count(*) FROM skills", Integer.class))
        .isEqualTo(count);
    assertThat(get("/data/catalog/imports/" + v.path("id").asText()).path("state").asText())
        .isEqualTo("DRAFT");
    assertThat(h.ok(p.post(path(v), confirmation(v, true), key)).path("skillIds").size())
        .isEqualTo(1);
  }

  @Test
  void snapshotHasOnlyOwnersLearningDataAndNoCredentialsOrStoragePaths() throws Exception {
    var f = h.fixture();
    String task = h.task(f).path("id").asText();
    h.write(
        "PUT", "/tasks/" + task + "/note", Map.of("revision", 0, "body", "Private export note"));
    var response = h.c.get("/data/snapshot");
    var snapshot = h.ok(response);
    assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    assertThat(snapshot.path("format").asText()).isEqualTo("scarlet-nexus-learning-snapshot/v1");
    assertThat(snapshot.path("tables").path("learning_notes").toString())
        .contains("Private export note");
    assertThat(snapshot.toString())
        .doesNotContain("password_hash", "storage_key", "owner_id", "SCARLET_SESSION");
    UUID uid = UUID.randomUUID();
    String password = key();
    h.jdbc.update(
        "INSERT INTO users(id,email,password_hash) VALUES(?,?,?)",
        uid,
        uid + "@example.test",
        h.runtime
            .app
            .getBean(org.springframework.security.crypto.password.PasswordEncoder.class)
            .encode(password));
    var bob = h.login(uid + "@example.test", password);
    assertThat(h.ok(bob.get("/data/snapshot")).path("tables").path("learning_notes")).isEmpty();
    var preview = preview(get("/data/catalog?skillId=" + f.skill()));
    assertThat(bob.get("/data/catalog/imports/" + preview.path("id").asText()).statusCode())
        .isEqualTo(404);
    assertThat(bob.get("/data/catalog?skillId=" + f.skill()).statusCode()).isEqualTo(404);
  }
}
