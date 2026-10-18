package com.vegalife.unit.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.service.ai.AiProviderErrorClassifier;
import com.vegalife.shared.exception.AiProviderException;
import com.vegalife.shared.exception.AiProviderException.Reason;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Regression coverage for issue #152: a provider failure used to be collapsed into one opaque
 * exception, so a live 429 was reported to the client as a bare 502 with no usable signal. These
 * tests pin the classification the diagnosis depends on.
 */
class AiProviderErrorClassifierTest {

  private static final String OP = "chat completion";

  @Test
  void rateLimited_provider403_producesRateLimitedReasonAndStatus() {
    RuntimeException wrapped =
        new TransientAiException("429", httpStatusError(HttpStatusCode.valueOf(429)));

    AiProviderException result = AiProviderErrorClassifier.classify(wrapped, OP);

    assertThat(result.getReason()).isEqualTo(Reason.RATE_LIMITED);
    assertThat(result.getUpstreamStatus()).isEqualTo(429);
  }

  @Test
  void authFailure_producesAuthReason() {
    RuntimeException wrapped =
        new NonTransientAiException("unauthorized", httpStatusError(HttpStatusCode.valueOf(401)));

    AiProviderException result = AiProviderErrorClassifier.classify(wrapped, OP);

    assertThat(result.getReason()).isEqualTo(Reason.AUTH);
    assertThat(result.getUpstreamStatus()).isEqualTo(401);
  }

  @Test
  void forbidden_isAlsoAuthFailure() {
    AiProviderException result =
        AiProviderErrorClassifier.classify(
            new RuntimeException(httpStatusError(HttpStatusCode.valueOf(403))), OP);

    assertThat(result.getReason()).isEqualTo(Reason.AUTH);
  }

  @Test
  void readTimeout_producesTimeoutWithoutStatus() {
    AiProviderException result =
        AiProviderErrorClassifier.classify(
            new ResourceAccessException("timeout", new HttpTimeoutException("read timed out")), OP);

    assertThat(result.getReason()).isEqualTo(Reason.TIMEOUT);
    assertThat(result.getUpstreamStatus()).isNull();
  }

  @Test
  void serverError_producesUpstreamErrorWithStatus() {
    AiProviderException result =
        AiProviderErrorClassifier.classify(
            new HttpServerErrorException(HttpStatusCode.valueOf(503)), OP);

    assertThat(result.getReason()).isEqualTo(Reason.UPSTREAM_ERROR);
    assertThat(result.getUpstreamStatus()).isEqualTo(503);
  }

  @Test
  void unclassifiableFailure_doesNotClaimRateLimitOrAuth() {
    AiProviderException result =
        AiProviderErrorClassifier.classify(new RuntimeException("provider down"), OP);

    assertThat(result.getReason()).isEqualTo(Reason.UNKNOWN);
    assertThat(result.getUpstreamStatus()).isNull();
  }

  @Test
  void emptyReply_isDistinctFromUnknown() {
    AiProviderException result = AiProviderErrorClassifier.emptyReply(OP);

    assertThat(result.getReason()).isEqualTo(Reason.EMPTY_REPLY);
  }

  /**
   * The failure written this session: a 429 arriving several layers deep. Reading only the
   * outermost exception is what lost the status originally.
   */
  @Test
  void nestedStatus_isFoundSeveralLevelsDown() {
    Throwable deep =
        new RuntimeException(
            "wrapped",
            new NonTransientAiException("upstream", new RestClientResponseExceptionStub(429)));

    AiProviderException result = AiProviderErrorClassifier.classify(deep, OP);

    assertThat(result.getUpstreamStatus()).isEqualTo(429);
    assertThat(result.getReason()).isEqualTo(Reason.RATE_LIMITED);
  }

  @Test
  void messageNeverContainsTheProviderBody() {
    // A real provider body may echo request content; only the status is carried into the message.
    AiProviderException result =
        AiProviderErrorClassifier.classify(httpStatusError(HttpStatusCode.valueOf(500)), OP);

    assertThat(result.getMessage()).doesNotContain("sk-");
    assertThat(result.getMessage()).isEqualTo(OP + " failed at the provider (HTTP 500)");
  }

  private static HttpClientErrorException httpStatusError(HttpStatusCode status) {
    return HttpClientErrorException.create(status, "error", HttpHeaders.EMPTY, new byte[0], null);
  }

  /** Minimal {@code RestClientResponseException} so the chain-walk is exercised for real. */
  private static final class RestClientResponseExceptionStub
      extends org.springframework.web.client.RestClientResponseException {

    private final HttpStatusCode status;

    private RestClientResponseExceptionStub(int code) {
      super("provider error", code, "error", HttpHeaders.EMPTY, new byte[0], null);
      this.status = HttpStatusCode.valueOf(code);
    }
  }
}
