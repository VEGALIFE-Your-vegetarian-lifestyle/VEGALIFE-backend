package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.filter.EmbeddingRelevanceScorer;
import com.vegalife.filter.EmbeddingRelevanceScorer.Centroids;
import com.vegalife.filter.FilterProperties;
import com.vegalife.filter.RelevanceScore;
import com.vegalife.filter.RelevanceScore.Band;
import com.vegalife.infrastructure.embedding.HfEmbeddingModel;
import com.vegalife.infrastructure.embedding.HfEmbeddingProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Regenerates the committed centroids from the bilingual seed corpora via the real HuggingFace
 * embedding endpoint. Skipped entirely when {@code HF_TOKEN} is absent, so CI runs stay green
 * against the committed JSON (FR-006, Phase 8 design gate). Two modes: with {@code
 * CENTROIDS_WRITE=true} it rewrites {@code src/main/resources/filter/centroids.json} and re-embeds
 * the fixture paragraphs in {@code src/test/resources/filter/fixtures.json} (labels and texts
 * preserved) so the offline regression test always scores the same model's vectors; otherwise it
 * verifies the committed centroids still match a fresh computation (cosine >= 0.99). Both modes run
 * the calibration guardrail (BR-FILTER-004): under the configured thresholds every on-topic seed
 * must PASS and every off-topic seed must REJECT after mean-centering. The guardrail
 * warns-and-proceeds in write mode (recalibration iterates) but hard-fails in verify mode, so
 * inconsistent committed state can never pass unnoticed.
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "HF_TOKEN", matches = ".+")
class CentroidRegenerationTest {

  private static final Path SEEDS_ON_TOPIC = Path.of("src/main/resources/filter/seed-on-topic.txt");
  private static final Path SEEDS_OFF_TOPIC =
      Path.of("src/main/resources/filter/seed-off-topic.txt");
  private static final Path CENTROIDS_FILE = Path.of("src/main/resources/filter/centroids.json");
  private static final Path FIXTURES_FILE = Path.of("src/test/resources/filter/fixtures.json");
  private static final double MIN_CENTROID_COSINE = 0.99;

  private record FixtureEntry(String label, String text, float[] embedding) {}

  private record FixtureFile(
      String model, String captured, String note, List<FixtureEntry> fixtures) {}

  @Test
  void regenerateOrVerifyCentroids() throws IOException {
    List<String> onTopicSeeds = readSeeds(SEEDS_ON_TOPIC);
    List<String> offTopicSeeds = readSeeds(SEEDS_OFF_TOPIC);
    assertFalse(onTopicSeeds.isEmpty(), "on-topic seed corpus must not be empty");
    assertFalse(offTopicSeeds.isEmpty(), "off-topic seed corpus must not be empty");

    HfEmbeddingProperties properties = new HfEmbeddingProperties();
    properties.setApiKey(System.getenv("HF_TOKEN"));
    HfEmbeddingModel model = new HfEmbeddingModel(buildRestClient(properties), properties);

    List<float[]> onVectors = model.embed(onTopicSeeds);
    List<float[]> offVectors = model.embed(offTopicSeeds);
    assertEquals(onTopicSeeds.size(), onVectors.size());
    assertEquals(offTopicSeeds.size(), offVectors.size());

    float[] onTopicCentroid = centroid(onVectors);
    float[] offTopicCentroid = centroid(offVectors);
    float[] mean = mean(onVectors, offVectors);

    boolean writeMode = System.getenv("CENTROIDS_WRITE") != null;
    checkSeedBands(onVectors, offVectors, onTopicCentroid, offTopicCentroid, mean, writeMode);

    if (writeMode) {
      ObjectMapper mapper = new ObjectMapper();
      Centroids centroids =
          new Centroids(onTopicCentroid.length, onTopicCentroid, offTopicCentroid, mean);
      String json =
          mapper.writerWithDefaultPrettyPrinter().writeValueAsString(centroids)
              + System.lineSeparator();
      Files.writeString(CENTROIDS_FILE, json, StandardCharsets.UTF_8);
      log.info(
          "wrote {} from {} on-topic and {} off-topic seed vectors",
          CENTROIDS_FILE,
          onVectors.size(),
          offVectors.size());
      refreshFixtures(mapper, model, properties, onTopicCentroid, offTopicCentroid, mean);
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
    assertTrue(
        EmbeddingRelevanceScorer.cosine(mean, committed.mean()) >= MIN_CENTROID_COSINE,
        "corpus mean drifted from the committed centroids.json; regenerate with "
            + "CENTROIDS_WRITE=true");
  }

  /**
   * Calibration guardrail: with the configured thresholds, every on-topic seed must PASS and every
   * off-topic seed must REJECT once centered, otherwise the committed centroids (or a future
   * regeneration) would ship the always-flagged bug again. Logs the observed score range so the
   * configured thresholds can be re-checked against it. Hard-fails in verify mode; in write mode
   * (recalibration in progress) it only warns and proceeds, judged from the logged margins.
   */
  private static void checkSeedBands(
      List<float[]> onVectors,
      List<float[]> offVectors,
      float[] onTopicCentroid,
      float[] offTopicCentroid,
      float[] mean,
      boolean writeMode) {
    FilterProperties properties = new FilterProperties();
    EmbeddingRelevanceScorer scorer =
        new EmbeddingRelevanceScorer(
            onTopicCentroid,
            offTopicCentroid,
            mean,
            properties.getAcceptThreshold(),
            properties.getRejectThreshold());
    double lowestOnScore = 1;
    double highestOffScore = 0;
    List<String> failures = new ArrayList<>();
    for (int i = 0; i < onVectors.size(); i++) {
      RelevanceScore result = scorer.score(onVectors.get(i));
      lowestOnScore = Math.min(lowestOnScore, result.score());
      if (result.band() != Band.PASS) {
        failures.add(
            "on-topic seed #"
                + i
                + " scored "
                + result.score()
                + " after centering; expected PASS (accept="
                + properties.getAcceptThreshold()
                + ")");
      }
    }
    for (int i = 0; i < offVectors.size(); i++) {
      RelevanceScore result = scorer.score(offVectors.get(i));
      highestOffScore = Math.max(highestOffScore, result.score());
      if (result.band() != Band.REJECT) {
        failures.add(
            "off-topic seed #"
                + i
                + " scored "
                + result.score()
                + " after centering; expected REJECT (reject="
                + properties.getRejectThreshold()
                + ")");
      }
    }
    log.info(
        "centered seed separation: lowest on-topic score={} (accept must stay <= this), "
            + "highest off-topic score={} (reject must stay > this); configured accept={} reject={}",
        lowestOnScore,
        highestOffScore,
        properties.getAcceptThreshold(),
        properties.getRejectThreshold());
    if (failures.isEmpty()) {
      return;
    }
    if (writeMode) {
      log.warn(
          "guardrail: {} seed band mismatch(es); continuing because CENTROIDS_WRITE is set: {}",
          failures.size(),
          String.join("; ", failures));
      return;
    }
    fail(String.join("; ", failures));
  }

  /**
   * Re-embeds every fixture paragraph with the same model and centroids just written, preserving
   * labels and texts, so {@code EmbeddingRelevanceScorerRegressionTest} always scores embeddings
   * from the model that produced the centroids. Logs each resulting band for visibility; bands are
   * not asserted here (write mode never blocks) — the regression test hard-checks them offline.
   */
  private static void refreshFixtures(
      ObjectMapper mapper,
      HfEmbeddingModel model,
      HfEmbeddingProperties hfProperties,
      float[] onTopicCentroid,
      float[] offTopicCentroid,
      float[] mean)
      throws IOException {
    FixtureFile file = mapper.readValue(FIXTURES_FILE.toFile(), FixtureFile.class);
    if (file.fixtures() == null || file.fixtures().isEmpty()) {
      throw new IllegalStateException("no fixture entries in " + FIXTURES_FILE);
    }
    List<String> texts = new ArrayList<>();
    for (FixtureEntry entry : file.fixtures()) {
      if (entry.text() == null || entry.text().isBlank()) {
        throw new IllegalStateException("fixture " + entry.label() + " lacks text; cannot embed");
      }
      texts.add(entry.text());
    }
    List<float[]> embeddings = model.embed(texts);
    assertEquals(texts.size(), embeddings.size());

    FilterProperties filterProperties = new FilterProperties();
    EmbeddingRelevanceScorer scorer =
        new EmbeddingRelevanceScorer(
            onTopicCentroid,
            offTopicCentroid,
            mean,
            filterProperties.getAcceptThreshold(),
            filterProperties.getRejectThreshold());
    List<FixtureEntry> refreshed = new ArrayList<>();
    for (int i = 0; i < file.fixtures().size(); i++) {
      FixtureEntry entry = file.fixtures().get(i);
      RelevanceScore result = scorer.score(embeddings.get(i));
      log.info(
          "fixture {} -> {} ({})",
          entry.label(),
          result.band(),
          String.format("%.4f", result.score()));
      refreshed.add(new FixtureEntry(entry.label(), entry.text(), embeddings.get(i)));
    }
    FixtureFile updated =
        new FixtureFile(
            hfProperties.getModel(), LocalDate.now().toString(), file.note(), refreshed);
    String json =
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(updated)
            + System.lineSeparator();
    Files.writeString(FIXTURES_FILE, json, StandardCharsets.UTF_8);
    log.info(
        "wrote {} ({} fixtures refreshed with model {})",
        FIXTURES_FILE,
        refreshed.size(),
        hfProperties.getModel());
  }

  /** Mean of the given vectors, without renormalization (the centering point itself). */
  private static float[] mean(List<float[]> first, List<float[]> second) {
    int dimensions = vectorDimensions(first);
    assertEquals(dimensions, vectorDimensions(second), "seed corpora dimensions must match");
    float[] sum = new float[dimensions];
    for (float[] vector : first) {
      addInto(sum, vector);
    }
    for (float[] vector : second) {
      addInto(sum, vector);
    }
    int count = first.size() + second.size();
    assertTrue(count > 0, "seed corpus must not be empty");
    for (int i = 0; i < dimensions; i++) {
      sum[i] = sum[i] / count;
    }
    return sum;
  }

  /** L2-normalized mean of the given vectors (the centroid direction). */
  private static float[] centroid(List<float[]> vectors) {
    int dimensions = vectorDimensions(vectors);
    float[] sum = new float[dimensions];
    for (float[] vector : vectors) {
      addInto(sum, vector);
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

  private static int vectorDimensions(List<float[]> vectors) {
    assertFalse(vectors.isEmpty(), "vector list must not be empty");
    int dimensions = vectors.getFirst().length;
    for (float[] vector : vectors) {
      assertEquals(dimensions, vector.length, "all vectors must share one dimensionality");
    }
    return dimensions;
  }

  private static void addInto(float[] sum, float[] vector) {
    for (int i = 0; i < sum.length; i++) {
      sum[i] += vector[i];
    }
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
