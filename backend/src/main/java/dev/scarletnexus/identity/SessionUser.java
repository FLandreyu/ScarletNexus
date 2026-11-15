package dev.scarletnexus.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class SessionUser extends User {
  private static final long serialVersionUID = 1L;
  private final UUID id;

  public SessionUser(UserAccount account) {
    super(
        account.email(),
        account.passwordHash(),
        "ACTIVE".equals(account.status()),
        true,
        true,
        true,
        List.of(new SimpleGrantedAuthority("USER")));
    this.id = account.id();
  }

  public UUID getId() {
    return id;
  }
}
