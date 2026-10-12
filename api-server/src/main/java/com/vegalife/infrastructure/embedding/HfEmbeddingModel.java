package com.vegalife.infrastructure.embedding;

import com.vegalife.shared.exception.EmbeddingClientException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Spring AI {@link EmbeddingModel} over HuggingFace's raw feature-extraction route ({@code POST
 * <base-url>/<model>} with body {@code {"inputs":[...],"normalize":true}} -> a matrix of rows, one
 * per input, in order). Route and model verified live per ADR-007.
 */
public class HfEmbeddingModel implements EmbeddingModel {

  private static final ParameterizedTypeReference<List<List<Float>>> ROWS_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;
  private final HfEmbeddingProperties properties;

  public HfEmbeddingModel(RestClient restClient, HfEmbeddingProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    List<String> inputs = request.getInstructions();
    if (inputs == null || inputs.isEmpty()) {
      return new EmbeddingResponse(List.of());
    }

    List<List<Float>> rows = fetchRows(inputs);

    if (rows.size() != inputs.size()) {
      throw new EmbeddingClientException(
          "HuggingFace embedding returned "
              + rows.size()
              + " rows for "
              + inputs.size()
              + " inputs (model "
              + properties.getModel()
              + ")");
    }

    List<Embedding> embeddings = new ArrayList<>(rows.size());
    for (int i = 0; i < rows.size(); i++) {
      List<Float> row = rows.get(i);
      if (row == null || row.isEmpty()) {
        throw new EmbeddingClientException(
            "HuggingFace embedding returned an empty row at index " + i);
      }
      float[] vector = new float[row.size()];
      for (int j = 0; j < vector.length; j++) {
        vector[j] = row.get(j);
      }
      embeddings.add(new Embedding(vector, i));
    }
    return new EmbeddingResponse(embeddings);
  }

  @Override
  public float[] embed(Document document) {
    return embed(getEmbeddingContent(document));
  }

  private List<List<Float>> fetchRows(List<String> inputs) {
    try {
      List<List<Float>> rows =
          restClient
              .post()
              .uri(endpoint())
              .contentType(MediaType.APPLICATION_JSON)
              .headers(
                  headers -> {
                    String key = properties.getApiKey();
                    if (key != null && !key.isBlank()) {
                      headers.setBearerAuth(key);
                    }
                  })
              .body(Map.of("inputs", inputs, "normalize", true))
              .retrieve()
              .body(ROWS_TYPE);
      if (rows == null) {
        throw new EmbeddingClientException(
            "HuggingFace embedding call returned an empty body (model "
                + properties.getModel()
                + ")");
      }
      return rows;
    } catch (RestClientException | HttpMessageConversionException e) {
      throw new EmbeddingClientException(
          "HuggingFace embedding call failed for model " + properties.getModel(), e);
    }
  }

  private String endpoint() {
    return properties.getBaseUrl().replaceAll("/+$", "") + "/" + properties.getModel();
  }
}
