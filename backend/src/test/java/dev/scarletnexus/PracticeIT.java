package dev.scarletnexus;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PracticeIT {
  LearningIT h;

  @BeforeAll
  void start() throws Exception {
    h = new LearningIT();
    h.start();
  }

  @AfterAll
  void close() throws Exception {
    h.stop();
  }

  JsonNode write(String path, Object body) throws Exception {
    return h.ok(h.c.write("POST", path, body));
  }

  JsonNode keyed(String path, Object body, String key) throws Exception {
    return h.ok(h.keyedResponse(h.c, path.substring("/learning".length()), body, key));
  }

  HttpResponse<String> post(String path, Object body, String key) throws Exception {
    String csrf = h.ok(h.c.get("/auth/csrf")).path("token").asText();
    var request = h.c.request("POST", path, body, csrf);
    return h.c.http.send(
        HttpRequest.newBuilder(request.uri())
            .headers(
                "Content-Type", "application/json", "X-CSRF-TOKEN", csrf, "Idempotency-Key", key)
            .POST(HttpRequest.BodyPublishers.ofString(h.json.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  String task() throws Exception {
    return h.task(h.fixture()).path("id").asText();
  }

  JsonNode question(String task, String type) throws Exception {
    return write(
        "/practice/tasks/" + task + "/questions",
        Map.of(
            "title",
            "Proof exercise",
            "prompt",
            "证明 $a^2+b^2\\geq 2ab$。",
            "questionType",
            type,
            "answerKey",
            "Private solution: (a-b)^2 >= 0",
            "hintText",
            "先考虑一个平方。"));
  }

  JsonNode submit(String task, String question, String content, String key) throws Exception {
    return h.ok(
        post(
            "/practice/tasks/" + task + "/attempts",
            Map.of(
                "questionId",
                question,
                "content",
                content,
                "declaredIndependent",
                true,
                "attachmentIds",
                List.of()),
            key));
  }

  @Test
  void independentAttemptHintsAndImmutableSubmissions() throws Exception {
    String t = task(), q = question(t, "PROOF").path("id").asText();
    String list = h.c.get("/practice/tasks/" + t + "/questions").body();
    assertThat(list).doesNotContain("answerKey", "hintText", "Private solution", "先考虑一个平方");
    String key = UUID.randomUUID().toString();
    var a = submit(t, q, "初次独立证明", key);
    assertThat(a.path("independent").asBoolean()).isTrue();
    assertThat(submit(t, q, "初次独立证明", key).path("id")).isEqualTo(a.path("id"));
    assertThat(
            post(
                    "/practice/tasks/" + t + "/attempts",
                    Map.of(
                        "questionId",
                        q,
                        "content",
                        "changed",
                        "declaredIndependent",
                        true,
                        "attachmentIds",
                        List.of()),
                    key)
                .statusCode())
        .isEqualTo(409);
    String id = a.path("id").asText();
    assertThat(
            h.c
                .write(
                    "POST",
                    "/practice/attempts/" + id + "/hint",
                    Map.of("level", "FULL_SOLUTION", "confirmed", false))
                .statusCode())
        .isEqualTo(422);
    assertThat(
            write(
                    "/practice/attempts/" + id + "/hint",
                    Map.of("level", "ONE_HINT", "confirmed", false))
                .path("text")
                .asText())
        .isEqualTo("先考虑一个平方。");
    var aided = submit(t, q, "提示后的改进证明", UUID.randomUUID().toString());
    assertThat(aided.path("independent").asBoolean()).isFalse();
    assertThat(aided.path("hintLevelAtSubmission").asInt()).isEqualTo(1);
    assertThat(
            h.ok(h.c.get("/practice/attempts/" + id))
                .path("attempt")
                .path("submittedContent")
                .asText())
        .isEqualTo("初次独立证明");
    assertThatThrownBy(
            () ->
                h.jdbc.update(
                    "UPDATE learning_attempts SET submitted_content='overwrite' WHERE id=?",
                    UUID.fromString(id)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void unsureProofCanBeReviewedButSelfReviewIsNeverVerifiedRuleEvidence() throws Exception {
    String t = task(),
        q = question(t, "PROOF").path("id").asText(),
        a = submit(t, q, "证明思路", UUID.randomUUID().toString()).path("id").asText();
    var rule = h.ok(post("/practice/attempts/" + a + "/check", null, UUID.randomUUID().toString()));
    assertThat(rule.path("verdict").asText()).isEqualTo("UNSURE");
    var review =
        h.ok(
            post(
                "/practice/attempts/" + a + "/reviews",
                Map.of("verdict", "CORRECT", "feedback", "逐步核对本人推导，仍属于自评。", "confirmed", true),
                UUID.randomUUID().toString()));
    String rid = review.path("id").asText();
    assertThat(
            h.c
                .write("POST", "/practice/reviews/" + rid + "/evidence", Map.of("confirmed", false))
                .statusCode())
        .isEqualTo(422);
    var e = write("/practice/reviews/" + rid + "/evidence", Map.of("confirmed", true));
    assertThat(e.path("verification").asText()).isEqualTo("SELF_REPORTED");
    assertThat(
            write("/practice/reviews/" + rid + "/evidence", Map.of("confirmed", true)).path("id"))
        .isEqualTo(e.path("id"));
    var assessed =
        write(
            "/practice/tasks/" + t + "/assessments",
            Map.of(
                "assessorType",
                "RULE",
                "level",
                5,
                "reason",
                "保守规则验收",
                "evidenceIds",
                List.of(e.path("id").asText())));
    assertThat(assessed.path("assessment").path("level").asInt()).isZero();
    var self =
        write(
            "/practice/tasks/" + t + "/assessments",
            Map.of(
                "assessorType",
                "SELF",
                "level",
                4,
                "reason",
                "本人自评，缺少外部验证",
                "evidenceIds",
                List.of(e.path("id").asText())));
    assertThat(self.path("assessment").path("confidence").asText()).isEqualTo("LOW");
    assertThat(h.ok(h.c.get("/practice/tasks/" + t + "/assessments")).path("total").asInt())
        .isEqualTo(2);
  }

  @Test
  void repeatedCorrectReviewsForOneQuestionCannotInflateLevelToFive() throws Exception {
    String t = task(),
        q = question(t, "EXACT_TEXT").path("id").asText(),
        a =
            submit(t, q, "Private solution: (a-b)^2 >= 0", UUID.randomUUID().toString())
                .path("id")
                .asText();
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      var r = h.ok(post("/practice/attempts/" + a + "/check", null, UUID.randomUUID().toString()));
      assertThat(r.path("verdict").asText()).isEqualTo("CORRECT");
      ids.add(
          write(
                  "/practice/reviews/" + r.path("id").asText() + "/evidence",
                  Map.of("confirmed", true))
              .path("id")
              .asText());
    }
    var assessment =
        write(
            "/practice/tasks/" + t + "/assessments",
            Map.of("assessorType", "RULE", "level", 5, "reason", "同一道题的重复记录", "evidenceIds", ids));
    assertThat(assessment.path("assessment").path("level").asInt()).isEqualTo(1);
  }

  static byte[] png(int width, int height) throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  HttpResponse<String> upload(String task, byte[] bytes, String mime) throws Exception {
    String boundary = "test-" + UUID.randomUUID(),
        csrf = h.ok(h.c.get("/auth/csrf")).path("token").asText();
    var out = new ByteArrayOutputStream();
    out.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"../../untrusted.png\"\r\nContent-Type: "
                + mime
                + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.write(bytes);
    out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return h.c.http.send(
        HttpRequest.newBuilder(
                h.c.request("GET", "/learning/tasks/" + task + "/attachments", null, null).uri())
            .headers(
                "Content-Type", "multipart/form-data; boundary=" + boundary, "X-CSRF-TOKEN", csrf)
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void imagesAreDecodedSanitizedPrivateAndArchivable() throws Exception {
    String t = task();
    var out = new ByteArrayOutputStream();
    out.write(png(16, 12));
    out.write("<script>original trailing payload</script>".getBytes(StandardCharsets.UTF_8));
    var file = h.ok(upload(t, out.toByteArray(), "image/png"));
    String id = file.path("id").asText();
    var response =
        h.c.http.send(
            h.c.request("GET", "/attachments/" + id + "/content", null, null),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(new String(response.body(), StandardCharsets.ISO_8859_1))
        .doesNotContain("trailing payload");
    assertThat(ImageIO.read(new ByteArrayInputStream(response.body())).getWidth()).isEqualTo(16);
    assertThat(response.headers().firstValue("content-type").orElse("")).isEqualTo("image/png");
    assertThat(response.headers().firstValue("cache-control").orElse("")).contains("no-store");
    assertThat(
            upload(t, "<html>not an image</html>".getBytes(StandardCharsets.UTF_8), "image/png")
                .statusCode())
        .isEqualTo(422);
    assertThat(upload(t, png(8001, 1), "image/png").statusCode()).isEqualTo(422);
    h.c.write("PATCH", "/attachments/" + id, Map.of("status", "ARCHIVED"));
    assertThat(h.c.get("/attachments/" + id + "/content").statusCode()).isEqualTo(404);
    assertThat(
            new IdentityCatalogIT.Client(h.runtime.port)
                .get("/attachments/" + id + "/content")
                .statusCode())
        .isEqualTo(401);
    assertThat(
            h.jdbc.queryForObject(
                "SELECT storage_key FROM attachments WHERE id=?",
                String.class,
                UUID.fromString(id)))
        .isEqualTo(id + ".png");
  }

  @Test
  void oversizedMultipartIsRejectedWithoutCreatingAnAttachment() throws Exception {
    String t = task();
    long before = h.jdbc.queryForObject("SELECT count(*) FROM attachments", Long.class);
    var response = upload(t, new byte[5242881], "image/png");
    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.body()).contains("IMAGE_SIZE");
    assertThat(h.jdbc.queryForObject("SELECT count(*) FROM attachments", Long.class))
        .isEqualTo(before);
  }

  @Test
  void anotherUserCannotReadAttachmentsAttemptsOrForgeEvidenceReferences() throws Exception {
    String t = task(),
        q = question(t, "PROOF").path("id").asText(),
        a = submit(t, q, "私有证明", UUID.randomUUID().toString()).path("id").asText();
    String image = h.ok(upload(t, png(2, 2), "image/png")).path("id").asText();
    UUID user = UUID.randomUUID();
    String password = "Practice-isolation-password";
    h.jdbc.update(
        "INSERT INTO users(id,email,password_hash,status) VALUES(?,?,?,'ACTIVE')",
        user,
        "practice-other@example.test",
        h.runtime
            .app
            .getBean(org.springframework.security.crypto.password.PasswordEncoder.class)
            .encode(password));
    var other = h.login("practice-other@example.test", password);
    assertThat(other.get("/practice/attempts/" + a).statusCode()).isEqualTo(404);
    assertThat(other.get("/attachments/" + image + "/content").statusCode()).isEqualTo(404);
    assertThat(other.get("/practice/tasks/" + t + "/questions").statusCode()).isEqualTo(404);
    assertThatThrownBy(
            () ->
                h.jdbc.update(
                    "INSERT INTO attempt_attachments(owner_id,attempt_id,attachment_id) VALUES(?,?,?)",
                    user,
                    UUID.fromString(a),
                    UUID.fromString(image)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void disabledTutorCannotDestroySavedAttempt() throws Exception {
    String t = task(),
        q = question(t, "PROOF").path("id").asText(),
        a = submit(t, q, "已提交的证明", UUID.randomUUID().toString()).path("id").asText();
    String c =
        write("/tutor/tasks/" + t + "/conversations", Map.of("questionId", q, "title", "练习讨论"))
            .path("id")
            .asText();
    assertThat(
            post(
                    "/tutor/conversations/" + c + "/stream",
                    Map.of("text", "请求复核", "mode", "SOCRATIC", "attemptId", a, "confirmed", true),
                    UUID.randomUUID().toString())
                .statusCode())
        .isEqualTo(503);
    assertThat(
            h.ok(h.c.get("/practice/attempts/" + a))
                .path("attempt")
                .path("submittedContent")
                .asText())
        .isEqualTo("已提交的证明");
  }
}
