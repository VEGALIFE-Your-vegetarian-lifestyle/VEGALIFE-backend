package com.vegalife.infrastructure.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vegalife.shared.exception.EmbeddingClientException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Spring AI {@link EmbeddingModel} over Gemini's {@code :batchEmbedContents} route (ADR-014).
 *
 * <p>Body shape: {@code {"requests":[{"model":"models/<m>","content":{"parts":[{"text":"..."}]},
 * "task_type":"SEMANTIC_SIMILARITY","output_dimensionality":768}, ...]}} -> {@code {"embeddings":
 * [{"values":[...]}, ...]}}, one entry per request in order.
 *
 * <p><strong>Every returned vector is L2-normalised before it is handed back.</strong> This is
 * load-bearing, not defensive: asking for {@code output_dimensionality} below the model's native
 * width truncates the vector and does <em>not</em> renormalise it. Measured for {@code
 * gemini-embedding-001} at 768 of 3072 dimensions, returned vectors have norm ≈ 0.59 rather than
 * 1.0. {@code EmbeddingRelevanceScorer}'s mean-centring arithmetic was derived against unit-length
 * vectors, so unnormalised input shifts every score with no error.
 *
 * <p>Do not remove the normalisation as "redundant": cosine similarity is scale-invariant, so the
 * regression checks that compare centroids would still pass with it deleted.
 */
public class GeminiEmbeddingModel implements EmbeddingModel {

  private final RestClient restClient;
  private final GeminiEmbeddingProperties properties;

  public GeminiEmbeddingModel(RestClient restClient, GeminiEmbeddingProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    List<String> inputs = request.getInstructions();
    if (inputs == null || inputs.isEmpty()) {
      return new EmbeddingResponse(List.of());
    }

    List<float[]> vectors = new ArrayList<>(inputs.size());
    for (int start = 0; start < inputs.size(); start += properties.getBatchSize()) {
      int end = Math.min(start + properties.getBatchSize(), inputs.size());
      vectors.addAll(embedBatch(inputs.subList(start, end), start));
    }

    List<Embedding> embeddings = new ArrayList<>(vectors.size());
    for (int i = 0; i < vectors.size(); i++) {
      embeddings.add(new Embedding(vectors.get(i), i));
    }
    return new EmbeddingResponse(embeddings);
  }

  @Override
  public float[] embed(Document document) {
    return embed(getEmbeddingContent(document));
  }

  /**
   * Reported from configuration, not probed.
   *
   * <p>{@link EmbeddingModel}'s default implementation discovers the width by issuing a live embed
   * call. That would make a purely local question ("how wide are these vectors?") depend on the
   * provider being reachable and on quota — and anything that asks during startup would fail to
   * boot whenever Gemini is down. The width is known: it is what we ask for.
   */
  @Override
  public int dimensions() {
    return properties.getDimensions();
  }

  /** Embeds one chunk; {@code offset} only improves error messages. */
  private List<float[]> embedBatch(List<String> inputs, int offset) {
    BatchRequest body = new BatchRequest(inputs.stream().map(this::toRequest).toList());

    BatchResponse response;
    try {
      response =
          restClient
              .post()
              .uri(endpoint())
              .contentType(MediaType.APPLICATION_JSON)
              .headers(headers -> headers.set("x-goog-api-key", properties.getApiKey()))
              .body(body)
              .retrieve()
              .body(BatchResponse.class);
    } catch (RuntimeException e) {
      throw new EmbeddingClientException(
          "Gemini embedding call failed for model " + properties.getModel(), e);
    }

    if (response == null || response.embeddings() == null) {
      throw new EmbeddingClientException(
          "Gemini embedding call returned no embeddings (model " + properties.getModel() + ")");
    }
    if (response.embeddings().size() != inputs.size()) {
      throw new EmbeddingClientException(
          "Gemini embedding returned "
              + response.embeddings().size()
              + " rows for "
              + inputs.size()
              + " inputs at offset "
              + offset
              + " (model "
              + properties.getModel()
              + ")");
    }

    List<float[]> rows = new ArrayList<>(inputs.size());
    for (int i = 0; i < inputs.size(); i++) {
      EmbeddingPayload payload = response.embeddings().get(i);
      if (payload == null || payload.values() == null || payload.values().isEmpty()) {
        throw new EmbeddingClientException(
            "Gemini embedding returned an empty vector at index " + (offset + i));
      }
      rows.add(toUnitVector(payload.values(), offset + i));
    }
    return rows;
  }

  /**
   * Converts the payload to a float array and scales it to unit L2 length.
   *
   * <p>A zero vector cannot be normalised and would produce NaN scores downstream, so it is
   * rejected here rather than allowed to poison the scorer.
   */
  private float[] toUnitVector(List<Float> values, int index) {
    float[] vector = new float[values.size()];
    double sumOfSquares = 0d;
    for (int i = 0; i < vector.length; i++) {
      Float value = values.get(i);
      if (value == null) {
        throw new EmbeddingClientException(
            "Gemini embedding has a null component at index " + index);
      }
      vector[i] = value;
      sumOfSquares += (double) value * value;
    }

    double norm = Math.sqrt(sumOfSquares);
    if (norm == 0d) {
      throw new EmbeddingClientException(
          "Gemini embedding returned a zero-length vector at index " + index);
    }
    float scale = (float) (1d / norm);
    for (int i = 0; i < vector.length; i++) {
      vector[i] *= scale;
    }
    return vector;
  }

  private EmbedRequest toRequest(String text) {
    return new EmbedRequest(
        "models/" + properties.getModel(),
        new Content(List.of(new Part(text))),
        GeminiTaskType.SEMANTIC_SIMILARITY,
        properties.getDimensions());
  }

  private String endpoint() {
    return properties.getBaseUrl().replaceAll("/+$", "")
        + "/models/"
        + properties.getModel()
        + ":batchEmbedContents";
  }

  // Wire types. Records keep the JSON shape legible and immovable; unknown fields are ignored by
  // the default Jackson configuration, so a provider adding fields cannot break the client.

  private record BatchRequest(List<EmbedRequest> requests) {}

  private record EmbedRequest(
      String model,
      Content content,
      @JsonProperty("task_type") String taskType,
      @JsonProperty("output_dimensionality") int outputDimensionality) {}

  private record Content(List<Part> parts) {}

  private record Part(String text) {}

  private record BatchResponse(List<EmbeddingPayload> embeddings) {}

  private record EmbeddingPayload(List<Float> values) {}
}
