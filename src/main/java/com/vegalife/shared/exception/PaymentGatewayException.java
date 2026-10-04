package com.vegalife.shared.exception;

/** Raised when the outbound payment gateway call fails: HTTP error, timeout, or unusable reply. */
public class PaymentGatewayException extends RuntimeException {

  public PaymentGatewayException(String message) {
    super(message);
  }

  public PaymentGatewayException(String message, Throwable cause) {
    super(message, cause);
  }
}
