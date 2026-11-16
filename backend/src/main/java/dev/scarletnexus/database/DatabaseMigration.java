package dev.scarletnexus.database;

import java.util.Map;
import org.flywaydb.core.Flyway;

/** One-shot migration process; deliberately does not start the web application. */
public final class DatabaseMigration {
  private DatabaseMigration() {}

  public static void main(String[] args) {
    try {
      migrate(System.getenv());
      System.out.println("Database migration and validation completed");
    } catch (Exception failure) {
      System.err.println("Database migration failed; application startup must remain blocked");
      System.exit(1);
    }
  }

  public static void migrate(Map<String, String> environment) {
    String url = required(environment, "DATABASE_URL");
    if (!url.startsWith("jdbc:postgresql://") || url.contains("?") || url.contains("@")) {
      throw new IllegalArgumentException(
          "Use a PostgreSQL URL without embedded credentials or options");
    }
    String username = required(environment, "MIGRATION_USERNAME");
    if (!username.equals("scarlet_migrator")) {
      throw new IllegalArgumentException("The isolated migration role is required");
    }
    var flyway =
        Flyway.configure()
            .dataSource(url, username, required(environment, "MIGRATION_PASSWORD"))
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .connectRetries(0)
            .lockRetryCount(10)
            .load();
    flyway.migrate();
    flyway.validate();
  }

  private static String required(Map<String, String> environment, String name) {
    String value = environment.get(name);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
    return value;
  }
}
