package dev.scarletnexus;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LearningIT {
  final ObjectMapper json = new ObjectMapper();
  LearningTestRuntime runtime;
  JdbcTemplate jdbc;
  IdentityCatalogIT.Client c;

  @BeforeAll
  void start() throws Exception {
    runtime = new LearningTestRuntime(0);
    jdbc = runtime.app.getBean(JdbcTemplate.class);
    c = login(runtime.email, runtime.password);
  }

  @AfterAll
  void stop() throws Exception {
    if (runtime != null) runtime.close();
  }

  IdentityCatalogIT.Client login(String email, String password) throws Exception {
    var client = new IdentityCatalogIT.Client(runtime.port);
    assertThat(
            client
                .write("POST", "/auth/login", Map.of("email", email, "password", password))
                .statusCode())
        .isEqualTo(200);
    return client;
  }

  JsonNode ok(HttpResponse<String> response) throws Exception {
    assertThat(response.statusCode())
        .withFailMessage("HTTP %s: %s", response.statusCode(), response.body())
        .isBetween(200, 299);
    return json.readTree(response.body());
  }

  JsonNode write(String method, String path, Object body) throws Exception {
    return ok(c.write(method, "/learning" + path, body));
  }

  JsonNode get(String path) throws Exception {
    return ok(c.get("/learning" + path));
  }

  JsonNode keyed(String path, Object body, String key) throws Exception {
    return ok(keyedResponse(c, path, body, key));
  }

  HttpResponse<String> keyedResponse(
      IdentityCatalogIT.Client client, String path, Object body, String key) throws Exception {
    String csrf = ok(client.get("/auth/csrf")).path("token").asText();
    var request = client.request("POST", "/learning" + path, body, csrf);
    return client.http.send(
        HttpRequest.newBuilder(request.uri())
            .headers(
                "Content-Type", "application/json", "X-CSRF-TOKEN", csrf, "Idempotency-Key", key)
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  record Fixture(String skill, String node) {}

  Fixture fixture() throws Exception {
    String skill =
        ok(c.write(
                "POST",
                "/skills",
                Map.of(
                    "name",
                    "Learning test " + UUID.randomUUID(),
                    "description",
                    "",
                    "category",
                    "",
                    "status",
                    "ACTIVE")))
            .path("id")
            .asText();
    String node =
        ok(c.write(
                "POST",
                "/knowledge/nodes",
                Map.of(
                    "title",
                    "Study node",
                    "description",
                    "Private definition",
                    "nodeType",
                    "CONCEPT",
                    "status",
                    "ACTIVE")))
            .path("id")
            .asText();
    ok(c.write("POST", "/skills/" + skill + "/nodes", Map.of("nodeId", node, "displayOrder", 0)));
    return new Fixture(skill, node);
  }

  Map<String, Object> goal(Fixture f) {
    return Map.of(
        "skillId",
        f.skill,
        "title",
        "Learn independently",
        "targetDescription",
        "Produce a small project",
        "deadline",
        "2026-12-31",
        "priority",
        2,
        "status",
        "ACTIVE",
        "revision",
        0);
  }

  Map<String, Object> route(Fixture f, String goal, String base, String title) {
    Map<String, Object> value = new HashMap<>();
    value.put("skillId", f.skill);
    value.put("goalId", goal);
    value.put("baseVersionId", base);
    value.put("title", title);
    value.put(
        "stages",
        List.of(
            Map.of(
                "title",
                "Foundation",
                "nodes",
                List.of(Map.of("nodeId", f.node, "targetLevel", 2, "required", true)))));
    return value;
  }

  JsonNode task(Fixture f) throws Exception {
    return write(
        "POST",
        "/tasks",
        Map.of("skillId", f.skill, "nodeId", f.node, "title", "Study task", "plannedMinutes", 25));
  }

  @Test
  void noAiLifecyclePreservesOldRouteProgressAndNotes() throws Exception {
    var f = fixture();
    var goal = write("POST", "/goals", goal(f));
    var first = write("POST", "/paths", route(f, goal.path("id").asText(), null, "First route"));
    String p1 = first.path("path").path("id").asText();
    var chosen = write("POST", "/enrollments", Map.of("pathId", p1));
    String en = chosen.path("enrollment").path("id").asText(),
        entry = first.path("entries").get(0).path("id").asText();
    assertThat(
            write("POST", "/enrollments", Map.of("pathId", p1))
                .path("enrollment")
                .path("id")
                .asText())
        .isEqualTo(en);
    assertThat(
            c.write("PATCH", "/learning/enrollments/" + en, Map.of("status", "COMPLETED"))
                .statusCode())
        .isEqualTo(422);
    var progress =
        write(
            "PUT",
            "/enrollments/" + en + "/progress/" + entry,
            Map.of("status", "COMPLETED", "revision", 0));
    assertThat(progress.path("revision").asInt()).isEqualTo(1);
    assertThat(
            c.write(
                    "PUT",
                    "/learning/enrollments/" + en + "/progress/" + entry,
                    Map.of("status", "IN_PROGRESS", "revision", 0))
                .statusCode())
        .isEqualTo(409);
    var task =
        write(
            "POST",
            "/tasks",
            Map.of(
                "skillId",
                f.skill,
                "goalId",
                goal.path("id").asText(),
                "nodeId",
                f.node,
                "enrollmentId",
                en,
                "entryId",
                entry,
                "title",
                "Route task",
                "plannedMinutes",
                30));
    String tid = task.path("id").asText();
    var note =
        write(
            "PUT",
            "/tasks/" + tid + "/note",
            Map.of("body", "Plain <script> text and learning notes", "revision", 0));
    assertThat(note.path("revision").asInt()).isEqualTo(1);
    assertThat(
            c.write(
                    "PUT",
                    "/learning/tasks/" + tid + "/note",
                    Map.of("body", "Overwrite", "revision", 0))
                .statusCode())
        .isEqualTo(409);
    var second = write("POST", "/paths", route(f, goal.path("id").asText(), p1, "Revised route"));
    assertThat(second.path("path").path("versionNumber").asInt()).isEqualTo(2);
    assertThat(
            c.write(
                    "POST",
                    "/learning/paths",
                    route(f, goal.path("id").asText(), p1, "Stale revision"))
                .statusCode())
        .isEqualTo(409);
    assertThat(get("/enrollments/" + en).path("completedRequired").asInt()).isEqualTo(1);
    assertThat(
            get("/tasks/" + tid + "/workbench")
                .path("route")
                .path("route")
                .path("path")
                .path("versionNumber")
                .asInt())
        .isEqualTo(1);
    write("POST", "/enrollments", Map.of("pathId", second.path("path").path("id").asText()));
    assertThat(get("/enrollments/" + en).path("enrollment").path("status").asText())
        .isEqualTo("PAUSED");
    write("POST", "/enrollments", Map.of("pathId", p1));
    write("PATCH", "/enrollments/" + en, Map.of("status", "COMPLETED"));
    ok(c.write("POST", "/auth/logout", null));
    c = login(runtime.email, runtime.password);
    assertThat(get("/tasks/" + tid + "/workbench").path("note").path("body").asText())
        .contains("learning notes");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE learning_paths SET title='changed' WHERE id=?", UUID.fromString(p1)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void timerCommandsAreIdempotentAndServerTimeSurvivesLogout() throws Exception {
    var f = fixture();
    String task = task(f).path("id").asText();
    String key = UUID.randomUUID().toString();
    var started = keyed("/sessions", Map.of("taskId", task), key);
    String sid = started.path("session").path("id").asText();
    assertThat(keyed("/sessions", Map.of("taskId", task), key).path("session").path("id").asText())
        .isEqualTo(sid);
    assertThat(
            keyedResponse(c, "/sessions", Map.of("taskId", task), UUID.randomUUID().toString())
                .statusCode())
        .isEqualTo(409);
    jdbc.update(
        "UPDATE learning_sessions SET started_at=now()-interval '70 seconds',segment_started_at=now()-interval '65 seconds' WHERE id=?",
        UUID.fromString(sid));
    String pause = UUID.randomUUID().toString();
    var paused =
        keyed("/sessions/" + sid + "/actions", Map.of("action", "PAUSE", "revision", 0), pause);
    long millis = paused.path("session").path("accumulatedMillis").asLong();
    assertThat(millis).isGreaterThanOrEqualTo(65000);
    assertThat(
            keyed("/sessions/" + sid + "/actions", Map.of("action", "PAUSE", "revision", 0), pause)
                .path("session")
                .path("accumulatedMillis")
                .asLong())
        .isEqualTo(millis);
    assertThat(
            keyedResponse(
                    c,
                    "/sessions/" + sid + "/actions",
                    Map.of("action", "RESUME", "revision", 1),
                    pause)
                .statusCode())
        .isEqualTo(409);
    ok(c.write("POST", "/auth/logout", null));
    c = login(runtime.email, runtime.password);
    assertThat(get("/dashboard").path("openSession").path("session").path("id").asText())
        .isEqualTo(sid);
    keyed(
        "/sessions/" + sid + "/actions",
        Map.of("action", "RESUME", "revision", 1),
        UUID.randomUUID().toString());
    assertThat(
            keyedResponse(
                    c,
                    "/sessions/" + sid + "/actions",
                    Map.of("action", "PAUSE", "revision", 0),
                    UUID.randomUUID().toString())
                .statusCode())
        .isEqualTo(409);
    var ended =
        keyed(
            "/sessions/" + sid + "/actions",
            Map.of("action", "FINISH", "revision", 2),
            UUID.randomUUID().toString());
    assertThat(ended.path("session").path("state").asText()).isEqualTo("ENDED");
    assertThat(ended.path("elapsedSeconds").asLong()).isGreaterThanOrEqualTo(65);
    assertThat(get("/dashboard").path("openSession").isNull()).isTrue();
    assertThat(get("/tasks/" + task + "/sessions").path("total").asInt()).isEqualTo(1);
  }

  @Test
  void concurrentStartsAdmitOneSessionAndRejectKeyReuse() throws Exception {
    String t = task(fixture()).path("id").asText();
    var a =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return keyedResponse(c, "/sessions", Map.of("taskId", t), "parallel-a")
                    .statusCode();
              } catch (Exception e) {
                throw new CompletionException(e);
              }
            });
    var b =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return keyedResponse(c, "/sessions", Map.of("taskId", t), "parallel-b")
                    .statusCode();
              } catch (Exception e) {
                throw new CompletionException(e);
              }
            });
    assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
    var open = get("/dashboard").path("openSession").path("session");
    keyed(
        "/sessions/" + open.path("id").asText() + "/actions",
        Map.of("action", "FINISH", "revision", 0),
        UUID.randomUUID().toString());
  }

  @Test
  void validatesResourcesRevisionsAndArchiveWithoutFetching() throws Exception {
    var f = fixture();
    String path = "/nodes/" + f.node + "/resources";
    for (String invalid :
        List.of("/relative/path", "https://user:pass@example.test/", "file:///tmp/a")) {
      assertThat(
              c.write(
                      "POST",
                      "/learning" + path,
                      Map.of(
                          "title",
                          "Invalid URL",
                          "resourceType",
                          "WEB",
                          "url",
                          invalid,
                          "status",
                          "ACTIVE",
                          "revision",
                          0))
                  .statusCode())
          .isEqualTo(422);
    }
    assertThat(
            c.write(
                    "POST",
                    "/learning" + path,
                    Map.of(
                        "title",
                        "Unsafe",
                        "resourceType",
                        "WEB",
                        "url",
                        "javascript:alert(1)",
                        "status",
                        "ACTIVE",
                        "revision",
                        0))
                .statusCode())
        .isEqualTo(422);
    var resource =
        write(
            "POST",
            path,
            Map.of(
                "title",
                "Reference",
                "resourceType",
                "WEB",
                "url",
                "https://example.test/never-fetched",
                "status",
                "ACTIVE",
                "revision",
                0));
    String id = resource.path("id").asText();
    var update =
        Map.of(
            "title",
            "Archived reference",
            "resourceType",
            "BOOK",
            "url",
            "https://example.test/book",
            "status",
            "ARCHIVED",
            "revision",
            0);
    write("PATCH", "/resources/" + id, update);
    assertThat(c.write("PATCH", "/learning/resources/" + id, update).statusCode()).isEqualTo(409);
    assertThat(get(path).path("items").get(0).path("status").asText()).isEqualTo("ARCHIVED");
    assertThat(c.get("/learning/tasks?size=101").statusCode()).isEqualTo(422);
  }

  @Test
  void continuationIsNotHiddenByRecentCompletedTasks() throws Exception {
    var f = fixture();
    var task = task(f);
    String tid = task.path("id").asText();
    write(
        "PATCH",
        "/tasks/" + tid,
        Map.of("title", "Continue me", "plannedMinutes", 25, "status", "DOING", "revision", 0));
    jdbc.update(
        "INSERT INTO learning_tasks(id,owner_id,skill_id,node_id,title,planned_minutes,status) SELECT gen_random_uuid(),t.owner_id,t.skill_id,t.node_id,'Recent completed task',25,'DONE' FROM learning_tasks t CROSS JOIN generate_series(1,12) WHERE t.id=?",
        UUID.fromString(tid));
    assertThat(get("/dashboard").path("continuationTask").path("id").asText()).isEqualTo(tid);
  }

  @Test
  void auditFailureRollsBackNoteWrite() throws Exception {
    String tid = task(fixture()).path("id").asText();
    write("PUT", "/tasks/" + tid + "/note", Map.of("body", "Saved before failure", "revision", 0));
    jdbc.execute(
        "CREATE FUNCTION fail_learning_note_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.entity_type='learning_note' THEN RAISE EXCEPTION 'injected learning audit failure'; END IF; RETURN NEW; END $$");
    jdbc.execute(
        "CREATE TRIGGER fail_learning_note_audit BEFORE INSERT ON audit_events FOR EACH ROW EXECUTE FUNCTION fail_learning_note_audit()");
    try {
      assertThat(
              c.write(
                      "PUT",
                      "/learning/tasks/" + tid + "/note",
                      Map.of("body", "Must roll back", "revision", 1))
                  .statusCode())
          .isEqualTo(503);
    } finally {
      jdbc.execute("DROP TRIGGER fail_learning_note_audit ON audit_events");
      jdbc.execute("DROP FUNCTION fail_learning_note_audit()");
    }
    assertThat(get("/tasks/" + tid + "/workbench").path("note").path("body").asText())
        .isEqualTo("Saved before failure");
    assertThat(get("/tasks/" + tid + "/workbench").path("note").path("revision").asInt())
        .isEqualTo(1);
  }

  @Test
  void isolatesForeignIdsAndEnforcesCompositeDatabaseOwnership() throws Exception {
    var f = fixture();
    String tid = task(f).path("id").asText();
    String foreign = UUID.randomUUID().toString(), password = "Other-learning-password";
    jdbc.update(
        "INSERT INTO users(id,email,password_hash,status) VALUES(?,?,?,'ACTIVE')",
        UUID.fromString(foreign),
        "learning-other@example.test",
        runtime.app.getBean(PasswordEncoder.class).encode(password));
    var other = login("learning-other@example.test", password);
    assertThat(other.get("/learning/tasks/" + tid + "/workbench").statusCode()).isEqualTo(404);
    assertThat(
            other
                .write(
                    "PUT",
                    "/learning/tasks/" + tid + "/note",
                    Map.of("body", "stolen", "revision", 0))
                .statusCode())
        .isEqualTo(404);
    assertThat(other.write("POST", "/learning/goals", goal(f)).statusCode()).isEqualTo(404);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO learning_notes(id,owner_id,task_id,body) VALUES(?,?,?,'foreign')",
                    UUID.randomUUID(),
                    UUID.fromString(foreign),
                    UUID.fromString(tid)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(new IdentityCatalogIT.Client(runtime.port).get("/learning/dashboard").statusCode())
        .isEqualTo(401);
  }
}
