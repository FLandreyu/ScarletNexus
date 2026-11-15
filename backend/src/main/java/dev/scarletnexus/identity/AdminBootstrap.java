package dev.scarletnexus.identity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AdminBootstrap implements ApplicationRunner {
  private final UserMapper users;
  private final PasswordEncoder encoder;
  private final TransactionTemplate transaction;
  private final String email;
  private final String passwordFile;

  public AdminBootstrap(
      UserMapper users,
      PasswordEncoder encoder,
      TransactionTemplate transaction,
      @Value("${app.bootstrap.email:}") String email,
      @Value("${app.bootstrap.password-file:}") String passwordFile) {
    this.users = users;
    this.encoder = encoder;
    this.transaction = transaction;
    this.email = email.strip().toLowerCase(Locale.ROOT);
    this.passwordFile = passwordFile;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (email.isBlank() && passwordFile.isBlank()) return;
    if (email.length() > 254
        || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || passwordFile.isBlank()) {
      throw new IllegalStateException(
          "Configure a valid ADMIN_EMAIL and ADMIN_PASSWORD_FILE together");
    }
    transaction.executeWithoutResult(
        status -> {
          users.lockBootstrap();
          if (users.count() != 0) return;
          String password;
          try {
            password =
                Files.readString(Path.of(passwordFile), StandardCharsets.UTF_8).stripTrailing();
          } catch (Exception error) {
            throw new IllegalStateException("Cannot read administrator bootstrap password file");
          }
          if (password.length() < 12
              || password.getBytes(StandardCharsets.UTF_8).length > 72
              || password.contains("\n")
              || password.contains("\r")) {
            throw new IllegalStateException(
                "Bootstrap password must be one line, at least 12 characters and at most 72 UTF-8 bytes");
          }
          UUID id = UUID.randomUUID();
          users.insert(id, email, encoder.encode(password));
          users.auditBootstrap(UUID.randomUUID(), id);
        });
  }
}
