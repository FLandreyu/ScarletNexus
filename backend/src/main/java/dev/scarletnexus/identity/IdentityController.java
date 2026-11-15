package dev.scarletnexus.identity;

import dev.scarletnexus.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
  private final AuthenticationManager manager;
  private final SecurityContextRepository contexts;
  private final LoginRateLimiter limiter;

  public IdentityController(
      AuthenticationManager manager, SecurityContextRepository contexts, LoginRateLimiter limiter) {
    this.manager = manager;
    this.contexts = contexts;
    this.limiter = limiter;
  }

  public record Me(UUID id, String email) {}

  public record Csrf(String headerName, String token) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() {
      return "Login[credentials=REDACTED]";
    }
  }

  @GetMapping("/auth/csrf")
  public Csrf csrf(CsrfToken token) {
    return new Csrf(token.getHeaderName(), token.getToken());
  }

  @GetMapping("/me")
  public Me me(@AuthenticationPrincipal SessionUser user) {
    return new Me(user.getId(), user.getUsername());
  }

  @PostMapping("/auth/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody Login login, HttpServletRequest request, HttpServletResponse response) {
    long retry = limiter.retryAfterSeconds(request.getRemoteAddr());
    if (retry > 0)
      return ResponseEntity.status(429)
          .header("Retry-After", String.valueOf(retry))
          .body(ApiError.of("LOGIN_RATE_LIMITED", "登录尝试过于频繁，请稍后重试", request));
    if (login.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BadCredentialsException("Invalid credentials");
    var authentication =
        manager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(login.email(), login.password()));
    new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
    new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository())
        .onAuthentication(authentication, request, response);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    var user = (SessionUser) authentication.getPrincipal();
    return ResponseEntity.ok(new Me(user.getId(), user.getUsername()));
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    new SecurityContextLogoutHandler()
        .logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    return ResponseEntity.noContent().build();
  }
}
