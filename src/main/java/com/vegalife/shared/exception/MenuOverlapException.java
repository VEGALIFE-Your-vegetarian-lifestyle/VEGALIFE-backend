package com.vegalife.shared.exception;

/**
 * Scheduling a menu would overlap another scheduled menu of the same user (BR-MENU-003); mapped to
 * HTTP 409. The write-time pre-check that raises this keeps V30's exclusion constraint from
 * surfacing as a raw persistence error.
 */
public class MenuOverlapException extends RuntimeException {

  public MenuOverlapException(String message) {
    super(message);
  }
}
