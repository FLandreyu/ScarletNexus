package dev.scarletnexus.common;

import org.springframework.http.HttpStatus;

public class DomainFailure extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public DomainFailure(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static DomainFailure missing() {
    return new DomainFailure(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在");
  }

  public static void require(boolean valid, String code, String message) {
    if (!valid) throw new DomainFailure(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
  }
}
