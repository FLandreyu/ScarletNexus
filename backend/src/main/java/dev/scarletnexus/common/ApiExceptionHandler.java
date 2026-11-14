package dev.scarletnexus.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ApiError> databaseUnavailable(
      DataAccessException error, HttpServletRequest request) {
    LOG.warn("Database operation unavailable: {}", error.getClass().getSimpleName());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiError.of("DATABASE_UNAVAILABLE", "数据库暂不可用，请稍后重试", request));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> notFound(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiError.of("NOT_FOUND", "资源不存在", request));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception error, HttpServletRequest request) {
    LOG.error("Unhandled request failure: {}", error.getClass().getSimpleName());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiError.of("INTERNAL_ERROR", "请求处理失败，请使用请求编号联系维护者", request));
  }
}
