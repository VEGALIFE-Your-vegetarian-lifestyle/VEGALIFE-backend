package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.filter.EmbeddingRelevanceScorer;
import com.vegalife.filter.FilterProperties;
import com.vegalife.filter.RelevanceScore;
import com.vegalife.filter.RelevanceScore.Band;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the always-flagged filter bug (every post landed NEEDS_REVIEW): raw e5
 * similarities are anisotropic, so the raw margin score spans only ~[0.45, 0.55] and never reaches
 * the accept/reject bands. Real paragraph-length title+body embeddings are committed under {@code
 * src/test/resources/filter/fixtures.json} and refreshed by {@code CentroidRegenerationTest}
 * whenever centroids are rewritten; with mean-centering and the thresholds from {@link
 * FilterProperties} each fixture must fall into its band: vegan content PASSes, spam REJECTs,
 * off-topic/MIX content stays REVIEW.
 */
class EmbeddingRelevanceScorerRegressionTest {

  private static final Map<String, Band> EXPECTED_BANDS =
      Map.of(
          "on-lentil-curry", Band.PASS,
          "on-pho-chay", Band.PASS,
          "off-crypto", Band.REJECT,
          "off-jobs-vi", Band.REJECT,
          "off-japan-travel", Band.REVIEW,
          "mix-steakhouse", Band.REVIEW);

  private record Fixture(String label, float[] embedding) {}

  @Test
  void paragraphFixturesFallIntoCalibratedBands() throws IOException {
    List<Fixture> fixtures = loadFixtures();
    assertEquals(
        EXPECTED_BANDS.size(),
        fixtures.size(),
        "fixture corpus changed; update EXPECTED_BANDS to match");

    EmbeddingRelevanceScorer.Centroids centroids = EmbeddingRelevanceScorer.loadCentroids();
    FilterProperties properties = new FilterProperties();
    EmbeddingRelevanceScorer scorer =
        new EmbeddingRelevanceScorer(
            centroids.onTopic(),
            centroids.offTopic(),
            centroids.mean(),
            properties.getAcceptThreshold(),
            properties.getRejectThreshold());

    for (Fixture fixture : fixtures) {
      Band expected = EXPECTED_BANDS.get(fixture.label());
      assertNotNull(expected, "unexpected fixture label: " + fixture.label());
      RelevanceScore result = scorer.score(fixture.embedding());
      assertEquals(
          expected,
          result.band(),
          () ->
              "fixture "
                  + fixture.label()
                  + " expected "
                  + expected
                  + " but scored "
                  + String.format("%.4f", result.score()));
    }
  }

  private static List<Fixture> loadFixtures() throws IOException {
    ObjectMapper mapper =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    try (InputStream in =
        EmbeddingRelevanceScorerRegressionTest.class.getResourceAsStream("/filter/fixtures.json")) {
      if (in == null) {
        throw new IllegalStateException("missing test resource /filter/fixtures.json");
      }
      FixtureFile file = mapper.readValue(in, FixtureFile.class);
      return file.fixtures();
    }
  }

  private record FixtureFile(String model, List<Fixture> fixtures) {}
}
