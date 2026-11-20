package com.vegalife.shared.exception;

/**
 * Raised when the caller has no AI requests left (BR-AI-001).
 *
 * <p>There is no reset to advertise: allowances never expire, so the request is refused until the
 * member acquires more. For that reason this exception deliberately carries no {@code Retry-After}
 * hint — waiting does not help.
 */
public class AiQuotaExceededException extends RuntimeException {

  public AiQuotaExceededException(String message) {
    super(message);
  }
}
