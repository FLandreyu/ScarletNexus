package dev.scarletnexus.desktop;

import dev.scarletnexus.ScarletNexusApplication;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** Private stdin control channel for the owning desktop process; no shutdown HTTP endpoint. */
public final class DesktopApplication {
  private DesktopApplication() {}

  public static void main(String[] args) throws Exception {
    if (!"true".equals(System.getenv("DESKTOP_MANAGED"))
        || !"scarlet_runtime".equals(System.getenv("DATABASE_USERNAME"))
        || !"false".equals(System.getenv("SPRING_FLYWAY_ENABLED"))
        || System.getenv("MIGRATION_PASSWORD") != null) {
      throw new IllegalStateException(
          "Desktop entry requires an isolated migrated runtime identity");
    }
    var context = new AtomicReference<ConfigurableApplicationContext>();
    var control =
        new Thread(
            () -> {
              try (var input =
                  new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String command;
                while ((command = input.readLine()) != null) {
                  if (command.equals("STOP")) break;
                }
              } catch (Exception ignored) {
                // Loss of the private parent channel also requests shutdown.
              } finally {
                var current = context.get();
                if (current != null) current.close();
                System.exit(0);
              }
            },
            "desktop-parent-control");
    control.start();
    try {
      context.set(
          SpringApplication.run(
              ScarletNexusApplication.class,
              "--server.address=127.0.0.1",
              "--spring.flyway.enabled=false"));
    } catch (Throwable failure) {
      System.err.println("Desktop service startup failed; see the private application log");
      System.exit(1);
    }
  }
}
