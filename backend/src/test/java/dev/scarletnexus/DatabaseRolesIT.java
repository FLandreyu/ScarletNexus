package dev.scarletnexus;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseRolesIT {
  @Test
  void migrationOwnsSchemaWhileRuntimePerformsRealWorkWithoutDdl() throws Exception {
    exerciseRoles(false);
  }

  @Test
  void independentJobMigratesBeforeRuntimeWithoutMigrationCredentials() throws Exception {
    exerciseRoles(true);
  }

  private void exerciseRoles(boolean independentJob) throws Exception {
    String runtimePassword = UUID.randomUUID().toString(),
        migrationPassword = UUID.randomUUID().toString();
    var settings = new HashMap<String, String>();
    settings.put("spring.datasource.username", "scarlet_runtime");
    settings.put("spring.datasource.password", runtimePassword);
    settings.put("spring.flyway.user", "scarlet_migrator");
    settings.put("spring.flyway.password", migrationPassword);
    if (independentJob) {
      settings.put("spring.flyway.enabled", "false");
      settings.put("spring.flyway.user", "scarlet_runtime");
      settings.put("spring.flyway.password", runtimePassword);
    }
    try (var runtime =
        new LearningTestRuntime(
            0,
            settings,
            (url, user, password) -> {
              // Embedded PG includes ?user=postgres; the URL must not override role credentials.
              settings.put("spring.datasource.url", url.split("\\?")[0]);
              try (var connection = DriverManager.getConnection(url, user, password);
                  var statement = connection.createStatement()) {
                statement.execute(
                    Files.readString(Path.of("../scripts/provision-database-roles.sql")));
                statement.execute("ALTER ROLE scarlet_runtime PASSWORD '" + runtimePassword + "'");
                statement.execute(
                    "ALTER ROLE scarlet_migrator PASSWORD '" + migrationPassword + "'");
              }
              if (independentJob) {
                String cleanUrl = url.split("\\?")[0];
                assertThat(runMigrationJob(cleanUrl, "wrong-password")).isEqualTo(1);
                try (var connection = DriverManager.getConnection(url, user, password);
                    var statement = connection.createStatement();
                    var result =
                        statement.executeQuery(
                            "SELECT count(*) FROM pg_tables WHERE schemaname='public'")) {
                  result.next();
                  assertThat(result.getInt(1)).isZero();
                }
                assertThat(runMigrationJob(cleanUrl, migrationPassword)).isZero();
                assertThat(runMigrationJob(cleanUrl, migrationPassword)).isZero();
                try (var connection = DriverManager.getConnection(url, user, password);
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT count(*) FROM users")) {
                  result.next();
                  assertThat(result.getInt(1)).isZero();
                }
              }
            })) {
      if (independentJob) {
        assertThat(runtime.app.getBeansOfType(org.flywaydb.core.Flyway.class)).isEmpty();
        assertThat(runtime.app.getEnvironment().getProperty("spring.flyway.password"))
            .isEqualTo(runtimePassword);
      }
      var db = runtime.app.getBean(JdbcTemplate.class);
      assertThat(db.queryForObject("SELECT current_user", String.class))
          .isEqualTo("scarlet_runtime");
      assertThat(
              db.queryForObject(
                  "SELECT tableowner FROM pg_tables WHERE schemaname='public' AND tablename='learning_tasks'",
                  String.class))
          .isEqualTo("scarlet_migrator");
      for (String sql :
          List.of(
              "CREATE TABLE public.forbidden(id int)",
              "ALTER TABLE learning_tasks ADD COLUMN forbidden text",
              "UPDATE flyway_schema_history SET description='forbidden'",
              "UPDATE learning_evidence SET outcome='CORRECT'",
              "SET ROLE scarlet_migrator",
              "CREATE ROLE forbidden LOGIN"))
        assertThatThrownBy(() -> db.execute(sql))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThat(
              db.queryForObject(
                  "SELECT has_schema_privilege(current_user,'public','CREATE')", Boolean.class))
          .isFalse();
      var h = new LearningIT();
      h.runtime = runtime;
      h.jdbc = db;
      h.c = h.login(runtime.email, runtime.password);
      var task = h.task(h.fixture());
      String id = task.path("id").asText();
      h.write(
          "PUT",
          "/tasks/" + id + "/note",
          Map.of("body", "Runtime role writes an audited learning note", "revision", 0));
      h.keyed("/sessions", Map.of("taskId", id), UUID.randomUUID().toString());
      assertThat(h.get("/tasks/" + id + "/workbench").path("note").path("body").asText())
          .contains("audited");
      var p = new PracticeIT();
      p.h = h;
      String q = p.question(id, "EXACT_TEXT").path("id").asText();
      String attempt =
          p.submit(id, q, "Private solution: (a-b)^2 >= 0", UUID.randomUUID().toString())
              .path("id")
              .asText();
      var review =
          h.ok(
              p.post(
                  "/practice/attempts/" + attempt + "/check", null, UUID.randomUUID().toString()));
      h.ok(
          h.c.write(
              "POST",
              "/practice/reviews/" + review.path("id").asText() + "/evidence",
              Map.of("confirmed", true)));
      assertThat(db.queryForObject("SELECT count(*) FROM learning_evidence", Integer.class))
          .isEqualTo(1);
      assertThat(h.ok(h.c.get("/data/snapshot")).path("tables").path("learning_tasks")).hasSize(1);
      assertThat(h.ok(h.c.get("/system/status")).path("schemaVersion").asText()).isEqualTo("12");
    }
  }

  private int runMigrationJob(String url, String password) throws Exception {
    var builder =
        new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Dloader.main=dev.scarletnexus.database.DatabaseMigration",
            "-cp",
            Path.of("target/scarlet-nexus-0.1.0-SNAPSHOT.jar").toAbsolutePath().toString(),
            "org.springframework.boot.loader.launch.PropertiesLauncher");
    builder.environment().put("DATABASE_URL", url);
    builder.environment().put("MIGRATION_USERNAME", "scarlet_migrator");
    builder.environment().put("MIGRATION_PASSWORD", password);
    builder.redirectErrorStream(true);
    var process = builder.start();
    try {
      assertThat(process.waitFor(45, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      String output =
          new String(
              process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      assertThat(output).doesNotContain(password);
      return process.exitValue();
    } finally {
      process.destroyForcibly();
    }
  }
}
