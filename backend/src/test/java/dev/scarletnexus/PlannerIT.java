package dev.scarletnexus;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlannerIT {
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

  JsonNode write(String method, String path, Object v) throws Exception {
    return h.ok(h.c.write(method, path, v));
  }

  JsonNode keyed(String path, Object v, String key) throws Exception {
    return h.ok(p.post(path, v, key));
  }

  String tomorrow() {
    return LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
  }

  JsonNode draft(List<LearningIT.Fixture> fs, int budget, int cap) throws Exception {
    return write(
        "POST",
        "/planner/drafts",
        Map.of(
            "planDate",
            tomorrow(),
            "timeZone",
            "Asia/Shanghai",
            "budgetMinutes",
            budget,
            "skillLimits",
            fs.stream().map(f -> Map.of("skillId", f.skill(), "maxMinutes", cap)).toList()));
  }

  Map<String, Object> confirm(JsonNode d) throws Exception {
    return Map.of(
        "revision",
        d.path("draft").path("revision").asInt(),
        "dayRevision",
        get("/planner/days/" + tomorrow()).path("revision").asInt(),
        "confirmed",
        true);
  }

  String draftPath(JsonNode d) {
    return "/planner/drafts/" + d.path("draft").path("id").asText();
  }

  void update(JsonNode t, int minutes, String status) throws Exception {
    var body = new HashMap<String, Object>();
    body.put("title", t.path("title").asText());
    body.put("plannedMinutes", minutes);
    body.put("dueDate", null);
    body.put("status", status);
    body.put("revision", t.path("revision").asInt());
    write("PATCH", "/learning/tasks/" + t.path("id").asText(), body);
  }

  @Test
  void threeSkillsDraftConfirmIdempotencyHistoryAndMinuteGuard() throws Exception {
    var fs = List.of(h.fixture(), h.fixture(), h.fixture());
    String[] names = {"11408", "Java", "Blender"};
    List<JsonNode> tasks = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      var f = fs.get(i);
      write(
          "PATCH",
          "/skills/" + f.skill(),
          Map.of("name", names[i], "description", "", "category", "", "status", "ACTIVE"));
      tasks.add(h.task(f));
      h.task(f);
    }
    var old = get("/planner/days/" + tomorrow());
    var d = draft(fs, 75, 25);
    assertThat(d.path("selectedMinutes").asInt()).isEqualTo(75);
    assertThat(get("/planner/days/" + tomorrow())).isEqualTo(old);
    for (var t : tasks) assertThat(get("/learning/tasks/" + t.path("id").asText())).isEqualTo(t);
    var body = confirm(d);
    String key = key();
    var plan = keyed(draftPath(d) + "/confirm", body, key);
    assertThat(plan.path("items")).hasSize(3);
    assertThat(plan.path("plannedMinutes").asInt()).isEqualTo(75);
    assertThat(keyed(draftPath(d) + "/confirm", body, key)).isEqualTo(plan);
    assertThat(
            p.post(
                    draftPath(d) + "/confirm",
                    Map.of("revision", 0, "dayRevision", 0, "confirmed", false),
                    key())
                .statusCode())
        .isEqualTo(422);
    var selected = plan.path("items").get(0).path("current");
    var patch =
        Map.of(
            "title",
            selected.path("title").asText(),
            "plannedMinutes",
            30,
            "status",
            "TODO",
            "revision",
            selected.path("revision").asInt());
    assertThat(
            h.c
                .write("PATCH", "/learning/tasks/" + selected.path("id").asText(), patch)
                .statusCode())
        .isEqualTo(422);
    var day = get("/planner/days/" + tomorrow());
    write(
        "POST",
        "/planner/days/" + tomorrow() + "/cancel",
        Map.of("revision", day.path("revision").asInt(), "confirmed", true, "reason", "调整预算"));
    assertThat(get("/planner/plans/" + plan.path("plan").path("id").asText()).path("items"))
        .hasSize(3);
    assertThat(get("/planner/days/" + tomorrow() + "/history").path("total").asInt())
        .isGreaterThanOrEqualTo(1);
    update(selected, 30, "TODO");
  }

  @Test
  void staleDraftAndOverspendingAreRejectedWithoutMutation() throws Exception {
    var f = h.fixture();
    var t = h.task(f);
    h.task(f);
    var d = draft(List.of(f), 25, 25);
    var ids = new ArrayList<String>();
    d.path("items").forEach(i -> ids.add(i.path("id").asText()));
    assertThat(h.c.write("PATCH", draftPath(d), Map.of("revision", 0, "itemIds", ids)).statusCode())
        .isEqualTo(422);
    assertThat(get(draftPath(d)).path("draft").path("revision").asInt()).isZero();
    update(t, 26, "TODO");
    assertThat(get(draftPath(d)).path("sourceChanged").asBoolean()).isTrue();
    assertThat(p.post(draftPath(d) + "/confirm", confirm(d), key()).statusCode()).isEqualTo(409);
  }

  @Test
  void taskSplitDeferCancelKeepNotesAndBudgetHistory() throws Exception {
    var f = h.fixture();
    var t = h.task(f);
    String id = t.path("id").asText();
    h.write("PUT", "/tasks/" + id + "/note", Map.of("body", "Original notes", "revision", 0));
    var body =
        Map.of(
            "kind",
            "SPLIT",
            "revision",
            0,
            "reason",
            "分成两次练习",
            "parts",
            List.of(
                Map.of("title", "Part A", "minutes", 10),
                Map.of("title", "Part B", "minutes", 15)));
    String key = key();
    var split = keyed("/planner/tasks/" + id + "/adjustments", body, key);
    assertThat(keyed("/planner/tasks/" + id + "/adjustments", body, key)).isEqualTo(split);
    assertThat(split.path("childTaskIds")).hasSize(2);
    assertThat(h.get("/tasks/" + id + "/workbench").path("note").path("body").asText())
        .isEqualTo("Original notes");
    assertThat(h.get("/tasks/" + id).path("status").asText()).isEqualTo("CANCELLED");
    String child = split.path("childTaskIds").get(0).asText();
    keyed(
        "/planner/tasks/" + child + "/adjustments",
        Map.of(
            "kind",
            "DEFER",
            "revision",
            0,
            "dueDate",
            LocalDate.parse(tomorrow()).plusDays(3).toString(),
            "reason",
            "等待资料",
            "parts",
            List.of()),
        key());
    var d = draft(List.of(f), 100, 100);
    assertThat(d.path("items").findValuesAsText("reason").toString()).contains("延期");
    keyed(
        "/planner/tasks/" + child + "/adjustments",
        Map.of("kind", "CANCEL", "revision", 1, "reason", "取消练习", "parts", List.of()),
        key());
    assertThat(get("/planner/tasks/" + child + "/adjustments").path("total").asInt()).isEqualTo(2);
  }

  JsonNode schedule(String task) throws Exception {
    return write(
        "POST",
        "/reviews/schedules",
        Map.of(
            "taskId",
            task,
            "firstDate",
            LocalDate.now(ZoneOffset.UTC).minusDays(1).toString(),
            "timeZone",
            "Asia/Shanghai",
            "plannedMinutes",
            10,
            "reason",
            "复习初始安排"));
  }

  String evidence(String task, String question, String content) throws Exception {
    String a = p.submit(task, question, content, key()).path("id").asText();
    var r = keyed("/practice/attempts/" + a + "/check", null, key());
    return write(
            "POST",
            "/practice/reviews/" + r.path("id").asText() + "/evidence",
            Map.of("confirmed", true))
        .path("id")
        .asText();
  }

  @Test
  void reviewCyclesFreshEvidenceConservativeIntervalsAndTaskReuse() throws Exception {
    var f = h.fixture();
    String t = h.task(f).path("id").asText();
    String q = p.question(t, "EXACT_TEXT").path("id").asText();
    String old = evidence(t, q, "Private solution: (a-b)^2 >= 0");
    var r = schedule(t);
    String rid = r.path("schedule").path("id").asText(), path = "/reviews/schedules/" + rid;
    assertThat(
            p.post(
                    path + "/complete",
                    Map.of("revision", 0, "evidenceId", old, "reason", "旧作答"),
                    key())
                .statusCode())
        .isEqualTo(422);
    var d = draft(List.of(f), 10, 10);
    assertThat(d.path("items").get(0).path("kind").asText()).isEqualTo("REVIEW");
    assertThat(get(path).path("cycleTaskId").isNull()).isTrue();
    var plan = keyed(draftPath(d) + "/confirm", confirm(d), key());
    String cycle = plan.path("items").get(0).path("current").path("id").asText();
    var d2 = draft(List.of(f), 10, 10);
    var plan2 = keyed(draftPath(d2) + "/confirm", confirm(d2), key());
    assertThat(plan2.path("items").get(0).path("current").path("id").asText()).isEqualTo(cycle);
    String fresh = evidence(t, q, "Private solution: (a-b)^2 >= 0");
    var complete = Map.of("revision", 0, "evidenceId", fresh, "reason", "独立复测");
    String key = key();
    var after = keyed(path + "/complete", complete, key);
    assertThat(after.path("schedule").path("intervalDays").asInt()).isEqualTo(2);
    assertThat(keyed(path + "/complete", complete, key)).isEqualTo(after);
    assertThat(get("/learning/tasks/" + cycle).path("status").asText()).isEqualTo("DONE");
    assertThat(
            p.post(
                    path + "/complete",
                    Map.of("revision", 1, "evidenceId", fresh, "reason", "重复证据"),
                    key())
                .statusCode())
        .isEqualTo(422);
    String wrong = evidence(t, q, "wrong");
    var second =
        keyed(
            path + "/complete",
            Map.of("revision", 1, "evidenceId", wrong, "reason", "错误复测"),
            key());
    assertThat(second.path("schedule").path("intervalDays").asInt()).isEqualTo(1);
    assertThat(second.path("schedule").path("reason").asText()).contains("保守");
    assertThat(get(path + "/history").path("total").asInt()).isEqualTo(3);
  }

  @Test
  void prerequisiteAndTimezoneAndIsolation() throws Exception {
    var a = h.fixture();
    var b = h.fixture();
    h.task(a);
    h.task(b);
    write(
        "POST",
        "/knowledge/relations",
        Map.of(
            "sourceNodeId", a.node(), "targetNodeId", b.node(), "relationType", "PREREQUISITE_OF"));
    var d = draft(List.of(b), 100, 100);
    assertThat(d.path("selectedMinutes").asInt()).isZero();
    assertThat(d.path("items").get(0).path("reason").asText()).contains("先修");
    assertThat(
            h.c
                .write(
                    "POST",
                    "/planner/drafts",
                    Map.of(
                        "planDate",
                        tomorrow(),
                        "timeZone",
                        "garbage",
                        "budgetMinutes",
                        20,
                        "skillLimits",
                        List.of(Map.of("skillId", a.skill(), "maxMinutes", 20))))
                .statusCode())
        .isEqualTo(422);
    UUID uid = UUID.randomUUID();
    String password = UUID.randomUUID().toString();
    h.jdbc.update(
        "INSERT INTO users(id,email,password_hash) VALUES(?,?,?)",
        uid,
        uid + "@example.test",
        h.runtime
            .app
            .getBean(org.springframework.security.crypto.password.PasswordEncoder.class)
            .encode(password));
    var bob = h.login(uid + "@example.test", password);
    assertThat(bob.get(draftPath(d)).statusCode()).isEqualTo(404);
    assertThat(
            bob.write(
                    "POST",
                    "/reviews/schedules",
                    Map.of(
                        "taskId",
                        h.task(a).path("id").asText(),
                        "firstDate",
                        tomorrow(),
                        "timeZone",
                        "UTC",
                        "plannedMinutes",
                        10,
                        "reason",
                        "private"))
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void confirmationFailureRollsBackAllSnapshotsAndReviewTask() throws Exception {
    var f = h.fixture();
    String t = h.task(f).path("id").asText();
    var r = schedule(t);
    var d = draft(List.of(f), 10, 10);
    var before = get("/planner/days/" + tomorrow());
    int tasks = h.jdbc.queryForObject("SELECT count(*) FROM learning_tasks", Integer.class);
    h.jdbc.execute(
        "CREATE FUNCTION fail_plan_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected confirmation failure'; END $$");
    h.jdbc.execute(
        "CREATE TRIGGER fail_plan_test BEFORE INSERT ON daily_plan_items FOR EACH ROW EXECUTE FUNCTION fail_plan_test()");
    try {
      assertThat(p.post(draftPath(d) + "/confirm", confirm(d), key()).statusCode()).isEqualTo(503);
    } finally {
      h.jdbc.execute("DROP TRIGGER fail_plan_test ON daily_plan_items");
      h.jdbc.execute("DROP FUNCTION fail_plan_test()");
    }
    assertThat(get("/planner/days/" + tomorrow())).isEqualTo(before);
    assertThat(get(draftPath(d)).path("draft").path("state").asText()).isEqualTo("DRAFT");
    assertThat(h.jdbc.queryForObject("SELECT count(*) FROM learning_tasks", Integer.class))
        .isEqualTo(tasks);
    assertThat(
            get("/reviews/schedules/" + r.path("schedule").path("id").asText())
                .path("cycleTaskId")
                .isNull())
        .isTrue();
  }

  @Test
  void simultaneousConfirmationHasOneWinnerAndImmutableHistory() throws Exception {
    var f = h.fixture();
    h.task(f);
    var d = draft(List.of(f), 25, 25);
    var body = confirm(d);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var a = executor.submit(() -> p.post(draftPath(d) + "/confirm", body, key()).statusCode());
      var b = executor.submit(() -> p.post(draftPath(d) + "/confirm", body, key()).statusCode());
      assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
    } finally {
      executor.shutdownNow();
    }
    String pid =
        get("/planner/days/" + tomorrow()).path("activePlan").path("plan").path("id").asText();
    assertThatThrownBy(
            () ->
                h.jdbc.update(
                    "UPDATE daily_plans SET budget_minutes=1440 WHERE id=?", UUID.fromString(pid)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                h.jdbc.update("DELETE FROM daily_plan_items WHERE plan_id=?", UUID.fromString(pid)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void unfinishedTimerBlocksAdjustmentsAndPausedReviewCannotComplete() throws Exception {
    var f = h.fixture();
    String t = h.task(f).path("id").asText();
    var session = h.keyed("/sessions", Map.of("taskId", t), key());
    assertThat(
            p.post(
                    "/planner/tasks/" + t + "/adjustments",
                    Map.of("kind", "CANCEL", "revision", 1, "reason", "计时未结束", "parts", List.of()),
                    key())
                .statusCode())
        .isEqualTo(422);
    h.keyed(
        "/sessions/" + session.path("session").path("id").asText() + "/actions",
        Map.of("action", "FINISH", "revision", 0),
        key());
    var r = schedule(t);
    String path = "/reviews/schedules/" + r.path("schedule").path("id").asText();
    var adjusted =
        write(
            "PATCH",
            path,
            Map.of("revision", 0, "status", "PAUSED", "nextDate", tomorrow(), "reason", "本周暂停"));
    assertThat(adjusted.path("schedule").path("revision").asInt()).isEqualTo(1);
    assertThat(draft(List.of(f), 10, 10).path("items").findValuesAsText("reason").toString())
        .contains("暂停");
    assertThat(
            p.post(
                    path + "/complete",
                    Map.of(
                        "revision",
                        1,
                        "evidenceId",
                        UUID.randomUUID().toString(),
                        "reason",
                        "paused"),
                    key())
                .statusCode())
        .isEqualTo(422);
    assertThat(
            h.c
                .write(
                    "PATCH",
                    path,
                    Map.of(
                        "revision",
                        0,
                        "status",
                        "ACTIVE",
                        "nextDate",
                        tomorrow(),
                        "reason",
                        "过期修改"))
                .statusCode())
        .isEqualTo(409);
  }
}
