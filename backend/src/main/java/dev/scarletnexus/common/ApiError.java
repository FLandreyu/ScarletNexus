package dev.scarletnexus.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public record ApiError(String code, String message, List<String> details, String requestId) {
  public static ApiError of(String code, String message, HttpServletRequest request) {
    return new ApiError(
        code, message, List.of(), String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE)));
  }
}
