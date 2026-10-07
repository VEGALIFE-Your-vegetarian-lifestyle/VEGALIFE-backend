package com.vegalife.shared.exception;

/**
 * Raised when the caller has spent the monthly AI allowance (BR-AI-001). Carries Retry-After
 * seconds so the handler can advertise when the current UTC-month window resets.
 */
public class AiQuotaExceededException extends RuntimeException {

  private final long retryAfterSeconds;

  public AiQuotaExceededException(String message, long retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
