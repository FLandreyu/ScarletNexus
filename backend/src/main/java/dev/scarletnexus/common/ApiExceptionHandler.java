package dev.scarletnexus.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(DomainFailure.class)
  ResponseEntity<ApiError> domain(DomainFailure error, HttpServletRequest request) {
    return ResponseEntity.status(error.status())
        .body(ApiError.of(error.code(), error.getMessage(), request));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<ApiError> conflict(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiError.of("REFERENCE_CONFLICT", "记录已存在或被引用，请刷新后重试", request));
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ApiError> authentication(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiError.of("INVALID_CREDENTIALS", "邮箱或密码不正确", request));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.ServletRequestBindingException.class
  })
  ResponseEntity<ApiError> invalid(HttpServletRequest request) {
    return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", "参数无效，请检查输入", request));
  }

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
