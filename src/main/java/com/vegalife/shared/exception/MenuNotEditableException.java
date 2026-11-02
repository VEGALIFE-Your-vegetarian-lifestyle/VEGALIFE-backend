package com.vegalife.shared.exception;

/** A menu cannot be edited in its current status (BR-MENU-006); mapped to HTTP 409. */
public class MenuNotEditableException extends RuntimeException {

  public MenuNotEditableException(String message) {
    super(message);
  }
}
