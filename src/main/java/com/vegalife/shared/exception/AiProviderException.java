package com.vegalife.shared.exception;

/**
 * Raised when the AI provider call fails: HTTP error, timeout, or unusable payload.
 *
 * <p>Carries a machine-readable {@link Reason} and, when the failure came from an HTTP response,
 * the upstream status. Callers branch on the reason rather than parsing the message: collapsing 401
 * / 402 / 429 / 5xx into one opaque error is what made the chat 502 undiagnosable (issue #152),
 * since the provider's real status was present the whole time but never surfaced.
 */
public class AiProviderException extends RuntimeException {

  /** Why the provider call failed. Each maps to one outcome in the HTTP layer. */
  public enum Reason {
    /**
     * Upstream throttled us (HTTP 429). Retryable, and distinct from the member's monthly quota.
     */
    RATE_LIMITED,
    /** Upstream rejected our credential (HTTP 401/403). Never leaks whether the key is absent. */
    AUTH,
    /** Upstream was slow or unreachable (HTTP 408, read timeout). */
    TIMEOUT,
    /** Any other upstream 4xx/5xx. */
    UPSTREAM_ERROR,
    /** The provider answered successfully but with no usable content. */
    EMPTY_REPLY,
    /** Anything we could not classify. */
    UNKNOWN
  }

  private final Reason reason;

  /**
   * Upstream HTTP status, or {@code null} when the failure produced no response (timeout, blank).
   */
  private final Integer upstreamStatus;

  public AiProviderException(String message) {
    this(message, Reason.UNKNOWN, null, null);
  }

  public AiProviderException(String message, Throwable cause) {
    this(message, Reason.UNKNOWN, null, cause);
  }

  public AiProviderException(String message, Reason reason, Integer upstreamStatus) {
    this(message, reason, upstreamStatus, null);
  }

  public AiProviderException(
      String message, Reason reason, Integer upstreamStatus, Throwable cause) {
    super(message, cause);
    this.reason = reason;
    this.upstreamStatus = upstreamStatus;
  }

  public Reason getReason() {
    return reason;
  }

  public Integer getUpstreamStatus() {
    return upstreamStatus;
  }
}
