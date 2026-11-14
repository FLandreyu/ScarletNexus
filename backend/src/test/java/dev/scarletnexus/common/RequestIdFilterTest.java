package dev.scarletnexus.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
  @Test
  void acceptsBoundedSafeIdsAndReplacesUnsafeInput() {
    assertThat(RequestIdFilter.sanitize("client.req-1_2")).isEqualTo("client.req-1_2");
    for (String value : new String[] {"", "bad\r\nheader", "x".repeat(65)}) {
      assertThat(UUID.fromString(RequestIdFilter.sanitize(value))).isNotNull();
    }
    assertThat(UUID.fromString(RequestIdFilter.sanitize(null))).isNotNull();
  }

  @Test
  void correlatesResponseAndRestoresThreadContext() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    request.addHeader(RequestIdFilter.HEADER, "test-correlation");
    MDC.put("requestId", "outer");
    try {
      new RequestIdFilter()
          .doFilter(
              request,
              response,
              (req, res) -> assertThat(MDC.get("requestId")).isEqualTo("test-correlation"));
      assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("test-correlation");
      assertThat(request.getAttribute("requestId")).isEqualTo("test-correlation");
      assertThat(MDC.get("requestId")).isEqualTo("outer");
    } finally {
      MDC.remove("requestId");
    }
  }
}
