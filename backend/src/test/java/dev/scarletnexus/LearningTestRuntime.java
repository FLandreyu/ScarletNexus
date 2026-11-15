package dev.scarletnexus;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/** Disposable real database and HTTP application; no AI key or fake provider. */
public final class LearningTestRuntime implements AutoCloseable {
  final String email = "learning@example.test", password = UUID.randomUUID().toString();
  final Path passwordFile;
  final EmbeddedPostgres pg;
  final PostgreSQLContainer<?> container;
  final ServletWebServerApplicationContext app;
  final int port;

  LearningTestRuntime(int port) throws Exception {
    passwordFile = Files.createTempFile("scarlet-learning-", ".secret");
    Files.writeString(passwordFile, password);
    String url, user, pass;
    if ("container".equals(System.getProperty("test.database"))) {
      pg = null;
      container = new PostgreSQLContainer<>("postgres:17.11-alpine");
      container.start();
      url = container.getJdbcUrl();
      user = container.getUsername();
      pass = container.getPassword();
    } else {
      container = null;
      pg = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start();
      url = pg.getJdbcUrl("postgres", "postgres");
      user = "postgres";
      pass = "";
    }
    app =
        (ServletWebServerApplicationContext)
            new SpringApplicationBuilder(ScarletNexusApplication.class)
                .run(
                    "--server.address=127.0.0.1",
                    "--server.port=" + port,
                    "--debug=false",
                    "--spring.datasource.url=" + url,
                    "--spring.datasource.username=" + user,
                    "--spring.datasource.password=" + pass,
                    "--app.bootstrap.email=" + email,
                    "--app.bootstrap.password-file=" + passwordFile,
                    "--app.ai.enabled=false",
                    "--app.ai.generation-enabled=false");
    this.port = app.getWebServer().getPort();
  }

  public void close() throws Exception {
    try {
      app.close();
    } finally {
      if (pg != null) pg.close();
      if (container != null) container.stop();
      Files.deleteIfExists(passwordFile);
    }
  }

  public static void main(String[] args) throws Exception {
    Path metadata = Path.of(args[0]), stop = Path.of(args[1]);
    if (Files.exists(stop)) throw new IllegalArgumentException("Use new stop path");
    try (var runtime = new LearningTestRuntime(18080)) {
      Files.writeString(
          metadata,
          new ObjectMapper()
              .writeValueAsString(
                  Map.of(
                      "port",
                      runtime.port,
                      "email",
                      runtime.email,
                      "passwordFile",
                      runtime.passwordFile.toString())));
      while (!Files.exists(stop)) Thread.sleep(250);
    } finally {
      Files.deleteIfExists(metadata);
    }
  }
}
