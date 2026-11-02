package com.vegalife.shared.exception;

import java.time.Duration;

/**
 * A delivery failure the queue may retry, optionally carrying the provider's own retry hint.
 *
 * <p>Raised by a channel adapter when the failure is transient and the receiver asked to be
 * contacted again — a provider's {@code Retry-After}, for instance. The drainer schedules the next
 * attempt no earlier than {@link #getRetryAfter()}, so a hint of 40 seconds is not undercut by a
 * 10-second configured delay (ADR-020).
 *
 * <p>An adapter that throws anything else is treated as a permanent failure: the message is
 * abandoned immediately instead of burning the retry ladder to {@code app.outbound.max-age}.
 */
public class RetryableDeliveryException extends RuntimeException {

  private final Duration retryAfter;

  public RetryableDeliveryException(String message, Duration retryAfter, Throwable cause) {
    super(message, cause);
    this.retryAfter = retryAfter;
  }

  /** The earliest moment the receiver asked to be retried, or {@code null} when it gave no hint. */
  public Duration getRetryAfter() {
    return retryAfter;
  }
}
