package com.vegalife.service.ai;

import com.vegalife.shared.exception.AiProviderException;
import com.vegalife.shared.exception.AiProviderException.Reason;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Turns the exception chain Spring AI / WebClient produce into a classified {@link
 * AiProviderException}.
 *
 * <p>Spring AI wraps provider failures several layers deep: a bad status usually surfaces as a
 * {@link RestClientResponseException} (carrying the real HTTP code) nested inside a {@code
 * TransientAiException}/{@code NonTransientAiException}, sometimes inside a generic {@code
 * RuntimeException}. Walking the cause chain is therefore necessary — reading only the outermost
 * exception is exactly how the provider's 429 became an anonymous 502 (issue #152).
 *
 * <p>Deliberately a stateless utility rather than a bean: it has no dependencies and is used by the
 * streaming path, which runs on a reactor thread outside the request scope.
 */
public final class AiProviderErrorClassifier {

  /** Comparison key for "provider throttled us", not a response to build. */
  private static final int TOO_MANY_REQUESTS = 429;

  private static final int REQUEST_TIMEOUT = 408;

  private static final int UNAUTHORIZED = 401;

  private static final int FORBIDDEN = 403;

  /** Guards against a self-referential cause chain looping forever. */
  private static final int MAX_CAUSE_DEPTH = 10;

  private AiProviderErrorClassifier() {}

  /**
   * Classifies a failed provider call. The resulting message is built from the reason and status
   * alone, never from the provider's response body or any header, so it cannot echo a credential.
   *
   * @param failure the exception thrown by the provider call
   * @param operation short description used in the message, e.g. {@code "chat completion"}
   */
  public static AiProviderException classify(Throwable failure, String operation) {
    Integer status = findStatus(failure);
    Reason reason = reasonFor(failure, status);
    return new AiProviderException(describe(operation, reason, status), reason, status, failure);
  }

  /** Builds the exception for a provider reply that arrived but carried no usable content. */
  public static AiProviderException emptyReply(String operation) {
    return new AiProviderException(
        operation + " returned an empty reply", Reason.EMPTY_REPLY, null);
  }

  private static Reason reasonFor(Throwable failure, Integer status) {
    if (status != null) {
      if (status == TOO_MANY_REQUESTS) {
        return Reason.RATE_LIMITED;
      }
      if (status == UNAUTHORIZED || status == FORBIDDEN) {
        return Reason.AUTH;
      }
      if (status == REQUEST_TIMEOUT) {
        return Reason.TIMEOUT;
      }
      if (status >= 400) {
        return Reason.UPSTREAM_ERROR;
      }
    }
    if (isTimeout(failure)) {
      return Reason.TIMEOUT;
    }
    return Reason.UNKNOWN;
  }

  /** Walks the cause chain for the first exception that carries an HTTP status. */
  private static Integer findStatus(Throwable failure) {
    Throwable current = failure;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof RestClientResponseException withResponse) {
        HttpStatusCode code = withResponse.getStatusCode();
        if (code != null) {
          return code.value();
        }
      }
      current = nextCause(current);
    }
    return null;
  }

  private static boolean isTimeout(Throwable failure) {
    Throwable current = failure;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof HttpTimeoutException
          || current instanceof TimeoutException
          || current instanceof ResourceAccessException) {
        return true;
      }
      current = nextCause(current);
    }
    return false;
  }

  private static Throwable nextCause(Throwable current) {
    Throwable next = current.getCause();
    return next == current ? null : next;
  }

  private static String describe(String operation, Reason reason, Integer status) {
    return switch (reason) {
      case RATE_LIMITED -> operation + " was rate limited by the provider";
      case AUTH -> operation + " was rejected by the provider (authentication)";
      case TIMEOUT -> operation + " timed out";
      case UPSTREAM_ERROR ->
          status == null
              ? operation + " failed at the provider"
              : operation + " failed at the provider (HTTP " + status + ")";
      case EMPTY_REPLY -> operation + " returned an empty reply";
      case UNKNOWN -> operation + " failed";
    };
  }
}
