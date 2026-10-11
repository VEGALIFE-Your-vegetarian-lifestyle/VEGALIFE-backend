package com.vegalife.shared.exception;

/** A menu status change is not in the legal transition table (BR-MENU-007); mapped to HTTP 409. */
public class InvalidMenuTransitionException extends RuntimeException {

  public InvalidMenuTransitionException(String message) {
    super(message);
  }
}
