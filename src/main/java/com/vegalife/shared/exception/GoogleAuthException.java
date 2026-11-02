package com.vegalife.shared.exception;

/**
 * A Google sign-in could not be completed: the code was rejected, the token endpoint was
 * unreachable, or the returned ID token failed verification (ADR-017).
 *
 * <p>The message is safe to show to a client because it never carries the reason's detail — no
 * token, no client secret, no upstream body (NFR-SEC-001). The detail goes to the log only.
 */
public class GoogleAuthException extends RuntimeException {

  public GoogleAuthException(String message) {
    super(message);
  }

  public GoogleAuthException(String message, Throwable cause) {
    super(message, cause);
  }
}
