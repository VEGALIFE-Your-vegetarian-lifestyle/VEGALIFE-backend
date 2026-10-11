package com.vegalife.shared.exception;

/** Raised when the AI provider call fails: HTTP error, timeout, or unusable payload. */
public class AiProviderException extends RuntimeException {

  public AiProviderException(String message) {
    super(message);
  }

  public AiProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
