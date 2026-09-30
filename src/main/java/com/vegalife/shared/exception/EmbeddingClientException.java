package com.vegalife.shared.exception;

/** Raised when the HuggingFace embedding call fails: HTTP error, timeout, or unusable payload. */
public class EmbeddingClientException extends RuntimeException {

  public EmbeddingClientException(String message) {
    super(message);
  }

  public EmbeddingClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
