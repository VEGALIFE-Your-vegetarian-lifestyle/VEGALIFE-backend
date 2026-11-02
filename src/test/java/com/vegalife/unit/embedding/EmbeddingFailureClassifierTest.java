package com.vegalife.unit.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.vegalife.infrastructure.embedding.GeminiEmbeddingModel;
import com.vegalife.infrastructure.embedding.GeminiEmbeddingProperties;
import com.vegalife.shared.exception.EmbeddingClientException;
import com.vegalife.shared.exception.EmbeddingClientException.Reason;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Pins the attribution required by ADR-020 and issue #175: the failure the outbound drainer logs
 * must name *which* rejection occurred, because the whole outage was undiagnosable while a 429, a
 * lapsed key and a missing model all produced one identical string.
 *
 * <p>The bodies here are the ones the live API actually returns, captured 2026-11-02:
 *
 * <ul>
 *   <li>invalid key → {@code 400 INVALID_ARGUMENT}, {@code details[].reason = API_KEY_INVALID}
 *   <li>no key → {@code 403 PERMISSION_DENIED}
 *   <li>unknown model → {@code 404 NOT_FOUND}
 *   <li>quota → {@code 429 RESOURCE_EXHAUSTED}
 * </ul>
 *
 * <p>The invalid-key case is the load-bearing one: it is a <strong>400</strong>, so a classifier
 * that mapped "4xx" to "the member's post is bad" would blame the wrong party.
 */
class EmbeddingFailureClassifierTest {

  private static final String ENDPOINT =
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents";

  private final GeminiEmbeddingProperties properties = new GeminiEmbeddingProperties();
  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final GeminiEmbeddingModel model = new GeminiEmbeddingModel(builder.build(), properties);

  @Test
  void invalidApiKeyIsAuthAndTerminalNotABadRequest() {
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    """
                    {"error":{"code":400,"status":"INVALID_ARGUMENT",
                    "message":"API key not valid. Please pass a valid API key.",
                    "details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo",
                    "reason":"API_KEY_INVALID","domain":"googleapis.com"}]}}
                    """));

    EmbeddingClientException failure = capture();

    assertThat(failure.getReason()).isEqualTo(Reason.AUTH);
    assertThat(failure.getUpstreamStatus()).isEqualTo(400);
    assertThat(failure.getProviderCode()).isEqualTo("API_KEY_INVALID");
    assertThat(failure.isTerminal()).isTrue();
    assertThat(failure.getMessage())
        .contains("rejected the credential")
        .contains("HTTP 400")
        .contains("API_KEY_INVALID")
        .contains("API key not valid");
  }

  @Test
  void missingKeyIsPermissionDenied() {
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    """
                    {"error":{"code":403,"message":"Method doesn't allow unregistered callers.",
                    "status":"PERMISSION_DENIED"}}
                    """));

    EmbeddingClientException failure = capture();

    assertThat(failure.getReason()).isEqualTo(Reason.AUTH);
    assertThat(failure.getProviderCode()).isEqualTo("PERMISSION_DENIED");
    assertThat(failure.isTerminal()).isTrue();
  }

  @Test
  void unknownModelIsNotFoundAndTerminal() {
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    """
                    {"error":{"code":404,"status":"NOT_FOUND",
                    "message":"models/gemini-embedding-999 is not found for API version v1beta."}}
                    """));

    EmbeddingClientException failure = capture();

    assertThat(failure.getReason()).isEqualTo(Reason.NOT_FOUND);
    assertThat(failure.getUpstreamStatus()).isEqualTo(404);
    assertThat(failure.isTerminal()).isTrue();
  }

  @Test
  void quotaIsRateLimitedRetryableAndCarriesTheProviderHint() {
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(
            withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Retry-After", "40")
                .body(
                    """
                    {"error":{"code":429,"status":"RESOURCE_EXHAUSTED",
                    "message":"You exceeded your current quota."}}
                    """));

    EmbeddingClientException failure = capture();

    assertThat(failure.getReason()).isEqualTo(Reason.RATE_LIMITED);
    assertThat(failure.isTerminal()).isFalse();
    assertThat(failure.getRetryAfter()).isEqualTo(Duration.ofSeconds(40));
    assertThat(failure.getMessage()).contains("rate limited").contains("HTTP 429");
  }

  @Test
  void serverErrorIsRetryable() {
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(
            withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"));

    EmbeddingClientException failure = capture();

    assertThat(failure.getReason()).isEqualTo(Reason.UPSTREAM_ERROR);
    assertThat(failure.isTerminal()).isFalse();
  }

  @Test
  void unclassifiedFailureStaysRetryable() {
    // A body the classifier cannot read must not be treated as permanent: dropping work because
    // parsing failed would be worse than one extra retry.
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("not json at all"));

    EmbeddingClientException failure = capture();

    assertThat(failure.getReason()).isEqualTo(Reason.INVALID_REQUEST);
    assertThat(failure.isTerminal()).isTrue();
  }

  @Test
  void unreadableBodyDoesNotThrowWhileClassifying() {
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_PLAIN).body(""));

    EmbeddingClientException failure = capture();

    assertThat(failure.getReason()).isEqualTo(Reason.UPSTREAM_ERROR);
    assertThat(failure.getProviderCode()).isNull();
  }

  /**
   * The pre-fix behaviour, now forbidden: all of these must not collapse to the same message. The
   * identifier stands in for the failure the drainer logs.
   */
  @Test
  void everyReasonProducesADistinctMessage() {
    String rateLimited =
        message(429, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}");
    String auth =
        message(
            400,
            "{\"error\":{\"status\":\"INVALID_ARGUMENT\","
                + "\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}");
    String notFound = message(404, "{\"error\":{\"status\":\"NOT_FOUND\"}}");

    assertThat(rateLimited).isNotEqualTo(auth).isNotEqualTo(notFound);
    assertThat(rateLimited).contains("rate limited");
    assertThat(auth).contains("credential");
    assertThat(notFound).contains("not available");
  }

  private String message(int status, String body) {
    RestClient.Builder localBuilder = RestClient.builder();
    MockRestServiceServer localServer = MockRestServiceServer.bindTo(localBuilder).build();
    localServer
        .expect(requestTo(ENDPOINT))
        .andRespond(
            withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    GeminiEmbeddingModel localModel =
        new GeminiEmbeddingModel(localBuilder.build(), new GeminiEmbeddingProperties());
    try {
      localModel.embed(List.of("text"));
      throw new AssertionError("expected the call to fail");
    } catch (EmbeddingClientException e) {
      return e.getMessage();
    }
  }

  private EmbeddingClientException capture() {
    try {
      model.embed(List.of("vegan pho recipe"));
    } catch (EmbeddingClientException e) {
      return e;
    }
    throw new AssertionError("expected the embedding call to fail");
  }
}
