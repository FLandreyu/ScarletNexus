package dev.scarletnexus;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.concurrent.CountDownLatch;
import org.springframework.boot.SpringApplication;

/** Starts a disposable real PostgreSQL cluster for P0 browser acceptance only. */
public final class P0Preview {
  private P0Preview() {}

  public static void main(String[] args) throws Exception {
    EmbeddedPostgres postgres =
        EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    postgres.close();
                  } catch (Exception ignored) {
                    // The process may already have been stopped by the embedded PostgreSQL shutdown
                    // hook.
                  }
                }));
    SpringApplication.run(
        ScarletNexusApplication.class,
        "--spring.datasource.url=" + postgres.getJdbcUrl("postgres", "postgres"),
        "--spring.datasource.username=postgres",
        "--spring.datasource.password=",
        "--server.address=127.0.0.1",
        "--server.port=8080",
        "--debug=false");
    new CountDownLatch(1).await();
  }
}
