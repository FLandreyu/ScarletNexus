package dev.scarletnexus;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalyticsIT {
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

  JsonNode write(String path, Object body) throws Exception {
    return h.ok(h.c.write("POST", path, body));
  }

  JsonNode post(String path, Object body) throws Exception {
    return h.ok(p.post(path, body, key()));
  }

  String query(String skill) {
    return "/analytics?timeZone=UTC&skillId=" + skill;
  }

  String id(JsonNode node) {
    return node.path("id").asText();
  }

  @Test
  void completedTimeUsesEndDateAndCancelledTasksAreNotDenominator() throws Exception {
    var f = h.fixture();
    String done = id(h.task(f)), cancelled = id(h.task(f)), todo = id(h.task(f));
    UUID owner =
        h.jdbc.queryForObject(
            "SELECT owner_id FROM learning_tasks WHERE id=?", UUID.class, UUID.fromString(done));
    h.jdbc.update(
        "UPDATE learning_tasks SET status='DONE',created_at='2030-01-03T01:00:00Z' WHERE id=?",
        UUID.fromString(done));
    h.jdbc.update(
        "UPDATE learning_tasks SET status='CANCELLED',created_at='2030-01-03T01:00:00Z' WHERE id=?",
        UUID.fromString(cancelled));
    h.jdbc.update(
        "UPDATE learning_tasks SET created_at='2030-01-03T01:00:00Z' WHERE id=?",
        UUID.fromString(todo));
    h.jdbc.update(
        "INSERT INTO learning_sessions(id,owner_id,task_id,state,started_at,ended_at,accumulated_millis) VALUES(?,?,?,'ENDED','2030-01-02T22:30:00Z','2030-01-02T23:30:00Z',3600000)",
        UUID.randomUUID(),
        owner,
        UUID.fromString(done));
    UUID open = UUID.randomUUID();
    h.jdbc.update(
        "INSERT INTO learning_sessions(id,owner_id,task_id,state,started_at,accumulated_millis) VALUES(?,?,?,'PAUSED','2030-01-03T01:00:00Z',300000)",
        open,
        owner,
        UUID.fromString(todo));
    try {
      var report =
          get(
              "/analytics?from=2030-01-03&to=2030-01-03&timeZone=Asia/Shanghai&skillId="
                  + f.skill());
      var totals = report.path("totals");
      assertThat(totals.path("studyMilliseconds").asLong()).isEqualTo(3600000);
      assertThat(totals.path("tasks").asInt()).isEqualTo(3);
      assertThat(totals.path("taskDenominator").asInt()).isEqualTo(2);
      assertThat(totals.path("doneTasks").asInt()).isEqualTo(1);
      assertThat(totals.path("cancelledTasks").asInt()).isEqualTo(1);
      assertThat(report.path("days")).hasSize(1);
      assertThat(report.path("skills").get(0).path("totals")).isEqualTo(totals);
      assertThat(
              get(query(f.skill()) + "&from=2030-01-03&to=2030-01-03")
                  .path("totals")
                  .path("studyMilliseconds")
                  .asLong())
          .isZero();
    } finally {
      h.jdbc.update(
          "UPDATE learning_sessions SET state='ENDED',ended_at='2030-01-03T01:05:00Z' WHERE id=?",
          open);
    }
  }

  @Test
  void independentRuleAccuracyCountsAttemptsOnceAndKeepsSelfAssessmentSeparate() throws Exception {
    var f = h.fixture();
    String task = id(h.task(f)), q = id(p.question(task, "EXACT_TEXT"));
    String correct = id(p.submit(task, q, "Private solution: (a-b)^2 >= 0", key()));
    var checked = post("/practice/attempts/" + correct + "/check", null);
    post("/practice/attempts/" + correct + "/check", null);
    String wrong = id(p.submit(task, q, "wrong", key()));
    post("/practice/attempts/" + wrong + "/check", null);
    String proof = id(p.question(task, "PROOF")),
        unsure = id(p.submit(task, proof, "proof", key()));
    post("/practice/attempts/" + unsure + "/check", null);
    String self = id(p.submit(task, proof, "self only", key()));
    post(
        "/practice/attempts/" + self + "/reviews",
        Map.of("verdict", "CORRECT", "feedback", "仅本人自评结果", "confirmed", true));
    write(
        "/practice/attempts/" + correct + "/hint", Map.of("level", "ONE_HINT", "confirmed", false));
    String aided = id(p.submit(task, q, "Private solution: (a-b)^2 >= 0", key()));
    post("/practice/attempts/" + aided + "/check", null);
    var totals = get(query(f.skill())).path("totals");
    assertThat(totals.path("attempts").asInt()).isEqualTo(5);
    assertThat(totals.path("independentAttempts").asInt()).isEqualTo(4);
    assertThat(totals.path("judgedIndependentAttempts").asInt()).isEqualTo(2);
    assertThat(totals.path("correctIndependentAttempts").asInt()).isEqualTo(1);
    assertThat(totals.path("unsureOrUnreviewedAttempts").asInt()).isEqualTo(2);
    String evidence =
        id(write("/practice/reviews/" + id(checked) + "/evidence", Map.of("confirmed", true)));
    for (String type : List.of("RULE", "SELF", "SELF"))
      write(
          "/practice/tasks/" + task + "/assessments",
          Map.of(
              "assessorType",
              type,
              "level",
              4,
              "reason",
              "来源区分验收",
              "evidenceIds",
              List.of(evidence)));
    var assessed = get("/analytics/assessments?timeZone=UTC&skillId=" + f.skill());
    assertThat(assessed.path("total").asInt()).isEqualTo(2);
    assertThat(assessed.path("items").findValuesAsText("assessorType"))
        .containsExactlyInAnyOrder("RULE", "SELF");
    assertThat(assessed.path("items").get(0).path("evidenceIds").get(0).asText())
        .isEqualTo(evidence);
    assertThat(get("/analytics/evidence/" + evidence).path("taskId").asText()).isEqualTo(task);
  }

  @Test
  void invalidRangesAreRejectedAndAnotherUsersDataIsInvisible() throws Exception {
    var f = h.fixture();
    h.task(f);
    assertThat(h.c.get("/analytics?from=2020-01-01&to=2026-11-16").statusCode()).isEqualTo(422);
    assertThat(h.c.get("/analytics?timeZone=NoSuchZone").statusCode()).isEqualTo(422);
    assertThat(h.c.get("/analytics?from=2030-02-01&to=2030-01-01").statusCode()).isEqualTo(422);
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
    assertThat(bob.get(query(f.skill())).statusCode()).isEqualTo(404);
    assertThat(bob.get("/analytics/evidence/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
    assertThat(h.ok(bob.get("/analytics")).path("totals").path("tasks").asInt()).isZero();
    assertThat(h.ok(bob.get("/analytics")).path("skills")).isEmpty();
  }
}
