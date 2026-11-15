package dev.scarletnexus.identity;

import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
  private CurrentUser() {}

  public static UUID id() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof SessionUser user))
      throw new AuthenticationCredentialsNotFoundException("Authentication required");
    return user.getId();
  }
}
