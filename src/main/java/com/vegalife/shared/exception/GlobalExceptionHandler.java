package com.vegalife.shared.exception;

import com.vegalife.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
