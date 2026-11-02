package com.vegalife.infrastructure.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.shared.exception.EmbeddingClientException;
import com.vegalife.shared.exception.EmbeddingClientException.Reason;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Turns an embedding provider failure into a classified {@link EmbeddingClientException}: reason,
 * upstream status, the provider's own error code, and its retry hint.
 *
 * <p>An HTTP status alone is not enough. Measured against the live Gemini API on 2026-11-02: an
 * invalid API key returns <strong>400</strong> {@code INVALID_ARGUMENT}, while a bad request body
 * also returns 400 — so "4xx means the request was wrong" would put the blame on the member's post
 * when the real fault is a lapsed credential. {@code error.status} and {@code details[].reason} in
 * the body are what separate the two.
 *
 * <p>Deliberately a stateless utility, like the AI chat classifier it mirrors: it has no
 * dependencies and must stay usable from a scheduled thread.
 */
final class EmbeddingFailureClassifier {

  /** Comparison keys for "the provider throttled us" and its neighbours, not responses to build. */
  private static final int TOO_MANY_REQUESTS = 429;

  private static final int REQUEST_TIMEOUT = 408;

  private static final int UNAUTHORIZED = 401;

  private static final int FORBIDDEN = 403;

  private static final int NOT_FOUND = 404;

  private static final int SERVER_ERROR_FLOOR = 500;

  /** Header carrying the receiver's wait hint; Gemini's gRPC detail carries the same value. */
  private static final String RETRY_AFTER_HEADER = "Retry-After";

  /** Guards against a self-referential cause chain looping forever. */
  private static final int MAX_CAUSE_DEPTH = 10;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private EmbeddingFailureClassifier() {}

  /**
   * Classifies a failed embedding call.
   *
   * @param failure the exception thrown by the client
   * @param provider the client's display name, used only in the message
   * @param model the configured model, used only in the message
   */
  static EmbeddingClientException classify(Throwable failure, String provider, String model) {
    RestClientResponseException response = findResponse(failure);
    if (response != null) {
      return fromResponse(response, provider, model);
    }
    if (hasCause(failure, ResourceAccessException.class)) {
      return new EmbeddingClientException(
          provider
              + " embedding call timed out or could not reach the provider (model "
              + model
              + ")",
          Reason.TIMEOUT,
          null,
          null,
          null,
          failure);
    }
    return new EmbeddingClientException(
        provider + " embedding call failed for model " + model + ": unclassified failure",
        Reason.UNKNOWN,
        null,
        null,
        null,
        failure);
  }

  private static EmbeddingClientException fromResponse(
      RestClientResponseException response, String provider, String model) {
    HttpStatusCode code = response.getStatusCode();
    int status = code == null ? -1 : code.value();
    String body = response.getResponseBodyAsString();
    String providerCode = providerCode(body);
    String providerMessage = providerMessage(body);
    Reason reason = reasonFor(status, providerCode);
    Duration retryAfter = retryAfter(response, body);

    StringBuilder message =
        new StringBuilder(provider)
            .append(" embedding call failed for model ")
            .append(model)
            .append(": ")
            .append(describe(reason))
            .append(status > 0 ? " (HTTP " + status + ")" : "");
    if (providerCode != null) {
      message.append(" [").append(providerCode).append(']');
    }
    // The provider's own words are bounded: enough to identify the cause, not enough to wedge a
    // log line or echo a request body the provider decided to quote back.
    if (providerMessage != null && !providerMessage.isBlank()) {
      message.append(" ").append(truncate(providerMessage, 200));
    }

    return new EmbeddingClientException(
        message.toString(), reason, status > 0 ? status : null, providerCode, retryAfter, response);
  }

  /**
   * Maps status plus the provider's error code to a reason. The code is consulted first because a
   * 400 carries both a bad credential and a bad request, and only the code distinguishes them.
   */
  private static Reason reasonFor(int status, String providerCode) {
    String code = providerCode == null ? "" : providerCode.toUpperCase(Locale.ROOT);
    if (code.contains("API_KEY_INVALID")
        || code.contains("UNAUTHENTICATED")
        || code.contains("PERMISSION_DENIED")) {
      return Reason.AUTH;
    }
    if (code.contains("NOT_FOUND")) {
      return Reason.NOT_FOUND;
    }
    if (code.contains("RESOURCE_EXHAUSTED") || status == TOO_MANY_REQUESTS) {
      return Reason.RATE_LIMITED;
    }
    if (status == UNAUTHORIZED || status == FORBIDDEN) {
      return Reason.AUTH;
    }
    if (status == REQUEST_TIMEOUT) {
      return Reason.TIMEOUT;
    }
    if (status == NOT_FOUND) {
      return Reason.NOT_FOUND;
    }
    if (status >= SERVER_ERROR_FLOOR) {
      return Reason.UPSTREAM_ERROR;
    }
    if (status >= 400) {
      return Reason.INVALID_REQUEST;
    }
    return Reason.UNKNOWN;
  }

  /** Finds the first exception in the chain carrying a response. */
  private static RestClientResponseException findResponse(Throwable failure) {
    Throwable current = failure;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof RestClientResponseException withResponse) {
        return withResponse;
      }
      current = nextCause(current);
    }
    return null;
  }

  private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
    Throwable current = failure;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (type.isInstance(current)) {
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

  /**
   * Reads {@code error.details[].reason}, falling back to {@code error.status}.
   *
   * <p>The reason is more specific — {@code API_KEY_INVALID} versus {@code INVALID_ARGUMENT} — so
   * it is preferred when both exist. A body that is not the expected shape yields {@code null}
   * rather than an exception: classification must never be the reason a call fails.
   */
  private static String providerCode(String body) {
    JsonNode error = readError(body);
    if (error == null) {
      return null;
    }
    JsonNode details = error.path("details");
    if (details.isArray()) {
      for (JsonNode detail : details) {
        JsonNode reason = detail.path("reason");
        if (reason.isTextual() && !reason.asText().isBlank()) {
          return reason.asText();
        }
      }
    }
    JsonNode status = error.path("status");
    return status.isTextual() && !status.asText().isBlank() ? status.asText() : null;
  }

  private static String providerMessage(String body) {
    JsonNode error = readError(body);
    if (error == null) {
      return null;
    }
    JsonNode message = error.path("message");
    return message.isTextual() ? message.asText() : null;
  }

  private static JsonNode readError(String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      JsonNode root = MAPPER.readTree(body);
      JsonNode error = root.path("error");
      return error.isObject() ? error : null;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * The provider's own retry hint, from the {@code Retry-After} header when present, otherwise from
   * the gRPC {@code RetryInfo} detail in the body.
   *
   * <p>Gemini's quota rejections usually carry no header at all and put the hint in {@code
   * details[].retryDelay} instead, as a protobuf duration string ({@code "37s"}). Reading only the
   * header would silently discard every quota hint, which is the defect ADR-020 exists to fix.
   */
  private static Duration retryAfter(RestClientResponseException response, String body) {
    Duration fromHeader =
        parseRetryAfter(response.getResponseHeaders().getFirst(RETRY_AFTER_HEADER));
    return fromHeader != null ? fromHeader : retryDelay(body);
  }

  /**
   * Parses the {@code Retry-After} header, which may be delta-seconds or an HTTP date. Only the
   * numeric form is honoured; a date form is rare for this API and guessing it wrong would be worse
   * than falling back to normal backoff.
   */
  private static Duration parseRetryAfter(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      long seconds = Long.parseLong(value.trim());
      return seconds <= 0 ? null : Duration.ofSeconds(seconds);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Reads {@code error.details[].retryDelay}, a protobuf duration such as {@code "37s"}. */
  private static Duration retryDelay(String body) {
    JsonNode error = readError(body);
    if (error == null) {
      return null;
    }
    JsonNode details = error.path("details");
    if (!details.isArray()) {
      return null;
    }
    for (JsonNode detail : details) {
      JsonNode delay = detail.path("retryDelay");
      if (!delay.isTextual()) {
        continue;
      }
      String value = delay.asText().trim();
      if (!value.endsWith("s")) {
        continue;
      }
      try {
        double seconds = Double.parseDouble(value.substring(0, value.length() - 1));
        return seconds <= 0 ? null : Duration.ofMillis(Math.round(seconds * 1000));
      } catch (NumberFormatException e) {
        // Not the shape we expect; fall through to no hint rather than guessing.
        return null;
      }
    }
    return null;
  }

  private static String describe(Reason reason) {
    return switch (reason) {
      case RATE_LIMITED -> "the provider rate limited this call";
      case AUTH -> "the provider rejected the credential";
      case INVALID_REQUEST -> "the provider rejected the request";
      case NOT_FOUND -> "the configured model is not available";
      case TIMEOUT -> "the provider timed out";
      case UPSTREAM_ERROR -> "the provider failed";
      case UNKNOWN -> "unclassified failure";
    };
  }

  private static String truncate(String value, int max) {
    String singleLine = value.replaceAll("\\s+", " ").trim();
    return singleLine.length() <= max ? singleLine : singleLine.substring(0, max) + "…";
  }
}
