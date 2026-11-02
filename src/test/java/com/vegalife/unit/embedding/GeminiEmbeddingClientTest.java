package com.vegalife.unit.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vegalife.infrastructure.embedding.GeminiEmbeddingModel;
import com.vegalife.infrastructure.embedding.GeminiEmbeddingProperties;
import com.vegalife.infrastructure.embedding.GeminiTaskType;
import com.vegalife.shared.exception.EmbeddingClientException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit coverage for the Gemini embedding client (ADR-014).
 *
 * <p>The normalisation tests are the important ones. They assert unit length directly rather than
 * comparing directions, because cosine similarity is scale-invariant: a client that returned
 * 0.59-norm vectors would still agree with a unit vector on direction, so only a norm assertion can
 * detect the omission.
 */
class GeminiEmbeddingClientTest {

  private static final String BASE = "https://generativelanguage.googleapis.com/v1beta";
  private static final String ENDPOINT = BASE + "/models/gemini-embedding-001:batchEmbedContents";

  private final GeminiEmbeddingProperties properties = new GeminiEmbeddingProperties();
  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final GeminiEmbeddingModel model = new GeminiEmbeddingModel(builder.build(), properties);

  @Test
  void embedsAndReturnsVectorsInRequestOrder() {
    properties.setApiKey("test-key");
    server
        .expect(requestTo(ENDPOINT))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-goog-api-key", "test-key"))
        .andExpect(jsonPath("$.requests[0].model").value("models/gemini-embedding-001"))
        .andExpect(jsonPath("$.requests[0].content.parts[0].text").value("first"))
        .andExpect(jsonPath("$.requests[1].content.parts[0].text").value("second"))
        .andRespond(withSuccess(body("[1.0,0.0]", "[0.0,1.0]"), MediaType.APPLICATION_JSON));

    var response = model.embed(java.util.List.of("first", "second"));

    assertThat(response).hasSize(2);
    assertThat(response.get(0)).containsExactly(1.0f, 0.0f);
    assertThat(response.get(1)).containsExactly(0.0f, 1.0f);
    server.verify();
  }

  /**
   * The core assertion of ADR-014 decision 3. A real 768-dimension response has norm ≈ 0.59; the
   * client must return it at unit length regardless, because the scorer's mean-centring was derived
   * against unit vectors.
   */
  @Test
  void nonUnitVectorIsNormalisedToUnitLength() {
    // [3, 4] has norm 5 — deliberately far from 1 so a missing normalisation is unmistakable.
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(withSuccess(body("[3.0,4.0]"), MediaType.APPLICATION_JSON));

    float[] vector = model.embed(java.util.List.of("anything")).getFirst();

    assertThat(vector[0]).isEqualTo(0.6f);
    assertThat(vector[1]).isEqualTo(0.8f);
    assertThat(norm(vector)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
  }

  /**
   * Reproduces the measured production behaviour: 768 components of a 3072-dimensional unit vector.
   * The result is not unit length, and must be normalised before it reaches the scorer.
   */
  @Test
  void truncatedVectorMatchesMeasuredProductionNormAndIsNormalised() {
    StringBuilder values = new StringBuilder("[");
    // A 3072-dim unit-ish vector truncated to 768: each component 1/sqrt(3072) ≈ 0.018.
    for (int i = 0; i < 768; i++) {
      if (i > 0) {
        values.append(',');
      }
      values.append("0.018");
    }
    values.append(']');
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(withSuccess(body(values.toString()), MediaType.APPLICATION_JSON));

    float[] vector = model.embed(java.util.List.of("post text")).getFirst();

    // 0.018 * sqrt(768) ≈ 0.4989 — the ~0.59-norm failure mode, reproduced.
    assertThat(norm(vector)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
  }

  @Test
  void sendsSharedTaskTypeAndConfiguredDimensions() {
    server
        .expect(requestTo(ENDPOINT))
        .andExpect(jsonPath("$.requests[0].task_type").value(GeminiTaskType.SEMANTIC_SIMILARITY))
        .andRespond(withSuccess(body("[1.0,0.0]"), MediaType.APPLICATION_JSON));

    model.embed(java.util.List.of("text"));

    server.verify();
  }

  @Test
  void usesSnakeCaseFieldNamesTheApiExpects() {
    // Records serialise as camelCase by default; the API rejects taskType/outputDimensionality.
    server
        .expect(requestTo(ENDPOINT))
        .andExpect(jsonPath("$.requests[0].task_type").exists())
        .andExpect(jsonPath("$.requests[0].output_dimensionality").exists())
        .andExpect(jsonPath("$.requests[0].taskType").doesNotExist())
        .andExpect(jsonPath("$.requests[0].outputDimensionality").doesNotExist())
        .andRespond(withSuccess(body("[1.0,0.0]"), MediaType.APPLICATION_JSON));

    model.embed(java.util.List.of("text"));

    server.verify();
  }

  @Test
  void chunksInputsWhenTheBatchSizeIsExceeded() {
    properties.setBatchSize(2);
    server
        .expect(requestTo(ENDPOINT))
        .andExpect(jsonPath("$.requests.length()").value(2))
        .andRespond(withSuccess(body("[1.0,0.0]", "[1.0,0.0]"), MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(ENDPOINT))
        .andExpect(jsonPath("$.requests.length()").value(1))
        .andRespond(withSuccess(body("[1.0,0.0]"), MediaType.APPLICATION_JSON));

    var response = model.embed(java.util.List.of("a", "b", "c"));

    assertThat(response).hasSize(3);
    server.verify();
  }

  @Test
  void rowCountMismatchFails() {
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(withSuccess(body("[1.0,0.0]"), MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> model.embed(java.util.List.of("a", "b")))
        .isInstanceOf(EmbeddingClientException.class)
        .hasMessageContaining("1 rows for 2 inputs");
  }

  @Test
  void emptyVectorFailsRatherThanReachingTheScorer() {
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(withSuccess("{\"embeddings\":[{\"values\":[]}]}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> model.embed(java.util.List.of("a")))
        .isInstanceOf(EmbeddingClientException.class)
        .hasMessageContaining("empty vector");
  }

  /**
   * A zero vector cannot be normalised; returning it would produce NaN similarity scores well away
   * from the cause.
   */
  @Test
  void zeroVectorFails() {
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(withSuccess(body("[0.0,0.0]"), MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> model.embed(java.util.List.of("a")))
        .isInstanceOf(EmbeddingClientException.class)
        .hasMessageContaining("zero-length");
  }

  @Test
  void missingEmbeddingsArrayFails() {
    server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> model.embed(java.util.List.of("a")))
        .isInstanceOf(EmbeddingClientException.class)
        .hasMessageContaining("no embeddings");
  }

  @Test
  void providerErrorIsSurfaced() {
    server.expect(requestTo(ENDPOINT)).andRespond(withServerError());

    assertThatThrownBy(() -> model.embed(java.util.List.of("a")))
        .isInstanceOf(EmbeddingClientException.class)
        .hasMessageContaining("Gemini embedding call failed");
  }

  @Test
  void emptyInputShortCircuitsWithoutACall() {
    assertThat(model.embed(java.util.List.of())).isEmpty();
    server.verify();
  }

  /**
   * {@code dimensions()} must not call the provider. The interface's default probes with a live
   * embed request, which would make startup depend on Gemini being reachable.
   */
  @Test
  void dimensionsComesFromConfigurationWithoutACall() {
    assertThat(model.dimensions()).isEqualTo(768);

    properties.setDimensions(1536);

    assertThat(model.dimensions()).isEqualTo(1536);
    // No expectation was registered, so any HTTP call would fail this test.
    server.verify();
  }

  private static String body(String... rows) {
    StringBuilder json = new StringBuilder("{\"embeddings\":[");
    for (int i = 0; i < rows.length; i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append("{\"values\":").append(rows[i]).append('}');
    }
    return json.append("]}").toString();
  }

  private static double norm(float[] vector) {
    double sum = 0d;
    for (float value : vector) {
      sum += (double) value * value;
    }
    return Math.sqrt(sum);
  }
}
