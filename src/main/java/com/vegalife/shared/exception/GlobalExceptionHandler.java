package com.vegalife.shared.exception;

import com.vegalife.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> errors = new HashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.put(error.getField(), error.getDefaultMessage());
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.failure("Validation failed"));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    Map<String, String> errors = new HashMap<>();
    ex.getConstraintViolations()
        .forEach(
            violation -> {
              String field = violation.getPropertyPath().toString();
              errors.put(field, violation.getMessage());
            });
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.failure("Validation failed"));
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      ValidationException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(ex.getMessage()));
  }

  @ExceptionHandler(DuplicateResourceException.class)
  public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(
      DuplicateResourceException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(ex.getMessage()));
  }

  @ExceptionHandler(InvalidTokenException.class)
  public ResponseEntity<ApiResponse<Void>> handleInvalidToken(
      InvalidTokenException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(ex.getMessage()));
  }

  @ExceptionHandler(ExpiredTokenException.class)
  public ResponseEntity<ApiResponse<Void>> handleExpiredToken(
      ExpiredTokenException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(ex.getMessage()));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
      ResourceNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(ex.getMessage()));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ApiResponse<Void>> handleForbidden(
      ForbiddenException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(ex.getMessage()));
  }

  /**
   * Method-security denials ({@code @PreAuthorize}) surface here because the {@code permitAll}
   * catch-all lets the request reach the controller (ADR-009). An anonymous caller gets {@code 401}
   * to match the filter-chain contract; an authenticated caller without the required authority gets
   * {@code 403}.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
      AccessDeniedException ex, Authentication authentication) {
    boolean anonymous =
        authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken;
    if (anonymous) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(ApiResponse.failure("Unauthorized"));
    }
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure("Forbidden"));
  }

  @ExceptionHandler(AiQuotaExceededException.class)
  public ResponseEntity<ApiResponse<Void>> handleAiQuotaExceeded(
      AiQuotaExceededException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
        .body(ApiResponse.failure(ex.getMessage()));
  }

  @ExceptionHandler(AiProviderException.class)
  public ResponseEntity<ApiResponse<Void>> handleAiProvider(
      AiProviderException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.failure(ex.getMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    String message = "Data integrity violation";
    if (ex.getCause() != null && ex.getCause().getMessage() != null) {
      String causeMsg = ex.getCause().getMessage().toLowerCase();
      if (causeMsg.contains("unique") || causeMsg.contains("duplicate")) {
        message = "Email or username already exists";
      }
    }
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(message));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.failure("Internal server error"));
  }
}
