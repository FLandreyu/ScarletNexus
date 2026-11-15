package dev.scarletnexus;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class GenerationChainIT {
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
      for (String topic : List.of("success", "success", "invalid", "truncated")) {
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
      assertThat(runtime.calls.get()).isEqualTo(3);
      var jdbc = runtime.app.getBean(JdbcTemplate.class);
      assertThat(
              jdbc.queryForObject("SELECT count(*) FROM knowledge_graph_versions", Integer.class))
          .isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_nodes", Integer.class)).isZero();
      assertThat(
              jdbc.queryForObject("SELECT reserved FROM ai_money_days", java.math.BigDecimal.class))
          .isEqualByComparingTo("0");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_job_costs", Integer.class))
          .isEqualTo(3);
    }
  }
}
