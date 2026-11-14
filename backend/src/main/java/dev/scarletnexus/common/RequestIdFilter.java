package dev.scarletnexus.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
  public static final String HEADER = "X-Request-ID";
  public static final String ATTRIBUTE = "requestId";
  private static final Pattern SAFE = Pattern.compile("[a-zA-Z0-9._-]{1,64}");

  static String sanitize(String candidate) {
    return candidate != null && SAFE.matcher(candidate).matches()
        ? candidate
        : UUID.randomUUID().toString();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = sanitize(request.getHeader(HEADER));
    request.setAttribute(ATTRIBUTE, requestId);
    response.setHeader(HEADER, requestId);
    String previous = MDC.get(ATTRIBUTE);
    MDC.put(ATTRIBUTE, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      if (previous == null) {
        MDC.remove(ATTRIBUTE);
      } else {
        MDC.put(ATTRIBUTE, previous);
      }
    }
  }
}
