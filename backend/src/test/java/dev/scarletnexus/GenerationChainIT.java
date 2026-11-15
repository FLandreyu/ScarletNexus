package dev.scarletnexus;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class GenerationChainIT {
  private HttpResponse<String> post(
      IdentityCatalogIT.Client c, String path, Map<String, Object> data, String key)
      throws Exception {
    String csrf =
        GenerationTestRuntime.JSON.readTree(c.get("/auth/csrf").body()).get("token").asText();
    return c.http.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + c.port + "/api/v1" + path))
            .headers(
                "Content-Type", "application/json", "X-CSRF-TOKEN", csrf, "Idempotency-Key", key)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    GenerationTestRuntime.JSON.writeValueAsString(data)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void enabledHttpGenerationReservesSettlesAndNeverAutoPublishes() throws Exception {
    try (var runtime = new GenerationTestRuntime(0)) {
      var c = new IdentityCatalogIT.Client(runtime.port);
      assertThat(
              c.write(
                      "POST",
                      "/auth/login",
                      Map.of("email", runtime.email, "password", runtime.password))
                  .statusCode())
          .isEqualTo(200);
      var skill =
          GenerationTestRuntime.JSON
              .readTree(
                  c.write(
                          "POST",
                          "/skills",
                          Map.of(
                              "name",
                              "Protocol acceptance",
                              "description",
                              "",
                              "category",
                              "",
                              "status",
                              "ACTIVE"))
                      .body())
              .get("id")
              .asText();
      String success = null;
      for (String topic : List.of("success", "success", "invalid", "truncated", "forged-mapping")) {
        String csrf =
            GenerationTestRuntime.JSON.readTree(c.get("/auth/csrf").body()).get("token").asText();
        var request =
            HttpRequest.newBuilder(
                    URI.create(
                        "http://127.0.0.1:"
                            + runtime.port
                            + "/api/v1/skills/"
                            + skill
                            + "/graph/generation"))
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", csrf)
                .header("Idempotency-Key", topic)
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        GenerationTestRuntime.JSON.writeValueAsString(Map.of("topic", topic))))
                .build();
        var response = c.http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var job = GenerationTestRuntime.JSON.readTree(response.body());
        assertThat(job.get("state").asText())
            .isEqualTo(topic.equals("success") ? "SUCCEEDED" : "FAILED");
        if (topic.equals("success")) {
          if (success != null) assertThat(job.get("id").asText()).isEqualTo(success);
          success = job.get("id").asText();
          assertThat(c.get("/graph/drafts/" + job.get("draftId").asText()).body())
              .contains("本地协议验收节点");
          assertThat(c.get("/ai/jobs/" + success + "/cost").body())
              .contains("PRICED_USAGE", "test-v1");
        }
      }
      assertThat(runtime.calls.get()).isEqualTo(4);
      var jdbc = runtime.app.getBean(JdbcTemplate.class);
      assertThat(
              jdbc.queryForObject("SELECT count(*) FROM knowledge_graph_versions", Integer.class))
          .isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_nodes", Integer.class)).isZero();
      assertThat(
              jdbc.queryForObject("SELECT reserved FROM ai_money_days", java.math.BigDecimal.class))
          .isEqualByComparingTo("0");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_job_costs", Integer.class))
          .isEqualTo(4);
      String draftId =
          GenerationTestRuntime.JSON
              .readTree(c.get("/ai/jobs/" + success).body())
              .get("draftId")
              .asText();
      var original =
          GenerationTestRuntime.JSON.readTree(
              GenerationTestRuntime.JSON
                  .readTree(c.get("/graph/drafts/" + draftId).body())
                  .get("document")
                  .asText());
      var split =
          c.write(
              "POST",
              "/graph/drafts/" + draftId + "/transform",
              Map.of(
                  "revision",
                  1,
                  "kind",
                  "SPLIT",
                  "source",
                  original.get("nodes").get(0).get("id").asText(),
                  "titles",
                  List.of("Blender foundation", "Blender practice")));
      assertThat(split.statusCode()).isEqualTo(200);
      var splitDoc =
          GenerationTestRuntime.JSON.readTree(
              GenerationTestRuntime.JSON.readTree(split.body()).get("document").asText());
      assertThat(splitDoc.get("mappings").size()).isEqualTo(1);
      assertThat(
              c.write(
                      "POST",
                      "/graph/drafts/" + draftId + "/transform",
                      Map.of(
                          "revision",
                          1,
                          "kind",
                          "MERGE",
                          "source",
                          splitDoc.get("nodes").get(1).get("id").asText(),
                          "target",
                          splitDoc.get("nodes").get(0).get("id").asText()))
                  .statusCode())
          .isEqualTo(409);
      var merged =
          c.write(
              "POST",
              "/graph/drafts/" + draftId + "/transform",
              Map.of(
                  "revision",
                  2,
                  "kind",
                  "MERGE",
                  "source",
                  splitDoc.get("nodes").get(1).get("id").asText(),
                  "target",
                  splitDoc.get("nodes").get(0).get("id").asText()));
      assertThat(merged.statusCode()).isEqualTo(200);
      String csrf =
          GenerationTestRuntime.JSON.readTree(c.get("/auth/csrf").body()).get("token").asText();
      var publish =
          HttpRequest.newBuilder(
                  URI.create(
                      "http://127.0.0.1:"
                          + runtime.port
                          + "/api/v1/graph/drafts/"
                          + draftId
                          + "/publish"))
              .headers(
                  "Content-Type",
                  "application/json",
                  "X-CSRF-TOKEN",
                  csrf,
                  "Idempotency-Key",
                  "transform-publish")
              .POST(HttpRequest.BodyPublishers.ofString("{\"revision\":3,\"confirmed\":true}"))
              .build();
      assertThat(c.http.send(publish, HttpResponse.BodyHandlers.ofString()).statusCode())
          .isEqualTo(200);
      var history =
          GenerationTestRuntime.JSON.readTree(c.get("/skills/" + skill + "/graph/versions").body());
      var snapshot = GenerationTestRuntime.JSON.readTree(history.get(0).get("document").asText());
      assertThat(snapshot.get("schemaVersion").asInt()).isEqualTo(2);
      assertThat(snapshot.get("mappings").size()).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_nodes", Integer.class))
          .isEqualTo(1);
      UUID invalid =
          jdbc.queryForObject(
              "SELECT id FROM ai_generation_jobs WHERE request_key='invalid'", UUID.class);
      assertThat(
              post(
                      c,
                      "/ai/jobs/" + invalid + "/repair",
                      Map.of("confirmed", false),
                      "not-confirmed")
                  .statusCode())
          .isEqualTo(422);
      var repaired =
          post(c, "/ai/jobs/" + invalid + "/repair", Map.of("confirmed", true), "repair-success");
      assertThat(repaired.statusCode()).isEqualTo(200);
      var repairedJob = GenerationTestRuntime.JSON.readTree(repaired.body());
      assertThat(repairedJob.get("state").asText()).isEqualTo("SUCCEEDED");
      assertThat(repairedJob.get("repairDepth").asInt()).isEqualTo(1);
      assertThat(repairedJob.get("parentJobId").asText()).isEqualTo(invalid.toString());
      assertThat(
              GenerationTestRuntime.JSON
                  .readTree(
                      post(
                              c,
                              "/ai/jobs/" + invalid + "/repair",
                              Map.of("confirmed", true),
                              "repair-success-again")
                          .body())
                  .get("id")
                  .asText())
          .isEqualTo(repairedJob.get("id").asText());
      var badRoot =
          GenerationTestRuntime.JSON.readTree(
              post(
                      c,
                      "/skills/" + skill + "/graph/generation",
                      Map.of("topic", "always-invalid"),
                      "always-invalid")
                  .body());
      String parent = badRoot.get("id").asText();
      for (int depth = 1; depth <= 2; depth++) {
        var response =
            post(c, "/ai/jobs/" + parent + "/repair", Map.of("confirmed", true), "depth-" + depth);
        assertThat(response.statusCode()).isEqualTo(200);
        var child = GenerationTestRuntime.JSON.readTree(response.body());
        assertThat(child.get("repairDepth").asInt()).isEqualTo(depth);
        assertThat(child.get("state").asText()).isEqualTo("FAILED");
        parent = child.get("id").asText();
      }
      assertThat(
              post(c, "/ai/jobs/" + parent + "/repair", Map.of("confirmed", true), "depth-3")
                  .statusCode())
          .isEqualTo(422);
      assertThat(runtime.calls.get()).isEqualTo(8);
      assertThat(
              jdbc.queryForObject("SELECT count(*) FROM knowledge_graph_versions", Integer.class))
          .isEqualTo(1);
    }
  }
}
