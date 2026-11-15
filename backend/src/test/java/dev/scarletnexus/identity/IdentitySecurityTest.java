package dev.scarletnexus.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentitySecurityTest {
  @Test
  void credentialRequestHasSafeDiagnosticRepresentation() {
    var request = new IdentityController.Login("test@example.test", "sensitive-test-password");
    assertThat(request.toString())
        .doesNotContain("sensitive-test-password", "test@example.test")
        .contains("REDACTED");
  }
}
