package com.vegalife.shared.exception;

/** A menu payload violates a menu business rule (e.g. BR-MENU-005); mapped to HTTP 400. */
public class MenuValidationException extends RuntimeException {

  public MenuValidationException(String message) {
    super(message);
  }
}
