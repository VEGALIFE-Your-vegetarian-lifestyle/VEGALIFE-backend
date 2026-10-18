package com.vegalife.shared.exception;

/**
 * Raised when an embedding call fails: HTTP error, timeout, or unusable payload.
 *
 * <p>Provider-neutral on purpose — both the active Gemini client and the dormant HuggingFace one
 * raise it, and the filter does not care which produced it.
 */
public class EmbeddingClientException extends RuntimeException {

  public EmbeddingClientException(String message) {
    super(message);
  }

  public EmbeddingClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
