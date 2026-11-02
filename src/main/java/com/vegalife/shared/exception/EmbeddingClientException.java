package com.vegalife.shared.exception;

import java.time.Duration;

/**
 * Raised when an embedding call fails: HTTP error, timeout, or unusable payload.
 *
 * <p>Provider-neutral on purpose — both the active Gemini client and the dormant HuggingFace one
 * raise it, and the filter does not care which produced it.
 *
 * <p>Carries a machine-readable {@link Reason}, the upstream status when there was one, and the
 * provider's retry hint if it gave one. Callers branch on the reason rather than parsing the
 * message: collapsing every rejection into one opaque string is what made an outbound message retry
 * 116 times without an operator being able to tell a quota limit from an expired key (issue #175,
 * ADR-020).
 */
public class EmbeddingClientException extends RuntimeException {

  /** Why the embedding call failed. Determines whether a retry can ever succeed. */
  public enum Reason {
    /** The provider throttled us. Retryable, but not earlier than {@link #getRetryAfter()}. */
    RATE_LIMITED,
    /** The provider rejected our credential (401/403, or a 400 naming an invalid API key). */
    AUTH,
    /** The provider rejected the request itself. Retrying it changes nothing. */
    INVALID_REQUEST,
    /** The configured model does not exist or is not served for this method. */
    NOT_FOUND,
    /** The provider was slow or unreachable. Retryable. */
    TIMEOUT,
    /** The provider failed on its side (5xx). Retryable. */
    UPSTREAM_ERROR,
    /** Anything that could not be classified. Retryable by default: unknown is not permanent. */
    UNKNOWN
  }

  private final Reason reason;

  /** Upstream HTTP status, or {@code null} when the failure produced no response. */
  private final Integer upstreamStatus;

  /** The provider's own error code (e.g. {@code API_KEY_INVALID}), or {@code null}. */
  private final String providerCode;

  /** How long the provider asked callers to wait, or {@code null} when it gave no hint. */
  private final Duration retryAfter;

  public EmbeddingClientException(String message) {
    this(message, Reason.UNKNOWN, null, null, null, null);
  }

  public EmbeddingClientException(String message, Throwable cause) {
    this(message, Reason.UNKNOWN, null, null, null, cause);
  }

  public EmbeddingClientException(
      String message,
      Reason reason,
      Integer upstreamStatus,
      String providerCode,
      Duration retryAfter,
      Throwable cause) {
    super(message, cause);
    this.reason = reason;
    this.upstreamStatus = upstreamStatus;
    this.providerCode = providerCode;
    this.retryAfter = retryAfter;
  }

  public Reason getReason() {
    return reason;
  }

  public Integer getUpstreamStatus() {
    return upstreamStatus;
  }

  public String getProviderCode() {
    return providerCode;
  }

  public Duration getRetryAfter() {
    return retryAfter;
  }

  /**
   * True when retrying cannot possibly succeed, so the delivery should stop now rather than consume
   * the retry ladder up to {@code app.outbound.max-age}.
   *
   * <p>{@link Reason#UNKNOWN} is deliberately <em>not</em> terminal: an unclassified failure is
   * more likely transient than permanent, and treating it as permanent would silently drop work.
   */
  public boolean isTerminal() {
    return reason == Reason.AUTH || reason == Reason.INVALID_REQUEST || reason == Reason.NOT_FOUND;
  }
}
