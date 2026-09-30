package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.filter.EmbeddingRelevanceScorer;
import com.vegalife.filter.EmbeddingRelevanceScorer.Centroids;
import com.vegalife.infrastructure.embedding.HfEmbeddingModel;
import com.vegalife.infrastructure.embedding.HfEmbeddingProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Regenerates the committed centroids from the bilingual seed corpora via the real HuggingFace
 * embedding endpoint. Skipped entirely when {@code HF_TOKEN} is absent, so CI runs stay green
 * against the committed JSON (FR-006, Phase 8 design gate). Two modes: with {@code
 * CENTROIDS_WRITE=true} it rewrites {@code src/main/resources/filter/centroids.json}; otherwise it
 * verifies the committed centroids still match a fresh computation (cosine >= 0.99).
 */
@EnabledIfEnvironmentVariable(named = "HF_TOKEN", matches = ".+")
class CentroidRegenerationTest {

  private static final Path SEEDS_ON_TOPIC = Path.of("src/main/resources/filter/seed-on-topic.txt");
  private static final Path SEEDS_OFF_TOPIC =
      Path.of("src/main/resources/filter/seed-off-topic.txt");
  private static final Path CENTROIDS_FILE = Path.of("src/main/resources/filter/centroids.json");
  private static final double MIN_CENTROID_COSINE = 0.99;

  @Test
  void regenerateOrVerifyCentroids() throws IOException {
    List<String> onTopicSeeds = readSeeds(SEEDS_ON_TOPIC);
    List<String> offTopicSeeds = readSeeds(SEEDS_OFF_TOPIC);
    assertFalse(onTopicSeeds.isEmpty(), "on-topic seed corpus must not be empty");
    assertFalse(offTopicSeeds.isEmpty(), "off-topic seed corpus must not be empty");

    HfEmbeddingProperties properties = new HfEmbeddingProperties();
    properties.setApiKey(System.getenv("HF_TOKEN"));
    HfEmbeddingModel model = new HfEmbeddingModel(buildRestClient(properties), properties);

    float[] onTopicCentroid = centroid(model, onTopicSeeds);
    float[] offTopicCentroid = centroid(model, offTopicSeeds);

    if (System.getenv("CENTROIDS_WRITE") != null) {
      ObjectMapper mapper = new ObjectMapper();
      Centroids centroids =
          new Centroids(onTopicCentroid.length, onTopicCentroid, offTopicCentroid);
      String json =
          mapper.writerWithDefaultPrettyPrinter().writeValueAsString(centroids)
              + System.lineSeparator();
      Files.writeString(CENTROIDS_FILE, json, StandardCharsets.UTF_8);
      return;
    }

    Centroids committed = EmbeddingRelevanceScorer.loadCentroids();
    assertEquals(onTopicCentroid.length, committed.dimensions());
    assertTrue(
        EmbeddingRelevanceScorer.cosine(onTopicCentroid, committed.onTopic())
            >= MIN_CENTROID_COSINE,
        "on-topic centroid drifted from the committed centroids.json; regenerate with "
            + "CENTROIDS_WRITE=true");
    assertTrue(
        EmbeddingRelevanceScorer.cosine(offTopicCentroid, committed.offTopic())
            >= MIN_CENTROID_COSINE,
        "off-topic centroid drifted from the committed centroids.json; regenerate with "
            + "CENTROIDS_WRITE=true");
  }

  private static float[] centroid(HfEmbeddingModel model, List<String> seeds) {
    List<float[]> vectors = model.embed(seeds);
    assertEquals(seeds.size(), vectors.size());
    int dimensions = vectors.getFirst().length;
    float[] sum = new float[dimensions];
    for (float[] vector : vectors) {
      assertEquals(dimensions, vector.length);
      for (int i = 0; i < dimensions; i++) {
        sum[i] += vector[i];
      }
    }
    double norm = 0;
    for (float value : sum) {
      norm += (double) value * value;
    }
    norm = Math.sqrt(norm);
    assertTrue(norm > 0, "centroid magnitude must be positive");
    for (int i = 0; i < dimensions; i++) {
      sum[i] = (float) (sum[i] / norm);
    }
    return sum;
  }

  private static RestClient buildRestClient(HfEmbeddingProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.getTimeout());
    requestFactory.setReadTimeout(properties.getTimeout());
    return RestClient.builder().requestFactory(requestFactory).build();
  }

  private static List<String> readSeeds(Path path) throws IOException {
    try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
      List<String> seeds =
          lines.map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
      return new ArrayList<>(seeds);
    }
  }
}
