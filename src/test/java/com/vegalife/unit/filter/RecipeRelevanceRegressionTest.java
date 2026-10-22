package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Offline regression test for the recipe centroid pair (ADR-016).
 *
 * <p>The recipe thresholds (0.60 / 0.38) were measured live against {@code gemini-embedding-001} at
 * 768 dimensions over the recipe seed corpora; see {@code docs/feats/filter-calibration-gemini.md}.
 * This test pins the consequence without touching the network: with the committed recipe centroids
 * and the committed thresholds, real recipe bodies must fall into their bands. If a future centroid
 * or threshold change would silently start flagging every real recipe — or start waving spam
 * through — this fails first.
 *
 * <p>Vectors are committed under {@code src/test/resources/filter/recipe-fixtures.json} and
 * refreshed by {@code CentroidRegenerationTest} whenever the recipe centroids are rewritten.
 */
class RecipeRelevanceRegressionTest {

  /**
   * Measured live over {@code seed-recipe-on-topic.txt} / {@code seed-recipe-off-topic.txt}. These
   * are the numbers the committed thresholds were chosen from, recorded so a later retune has to
   * confront them.
   */
  private static final double LOWEST_ON_TOPIC_SCORE = 0.7062474433867980;

  private static final double HIGHEST_OFF_TOPIC_SCORE = 0.2679377000816598;

  private static final Map<String, Band> EXPECTED_BANDS =
      Map.of(
          "on-canh-chua-chay", Band.PASS,
          "on-tofu-banh-mi", Band.PASS,
          "on-mushroom-pie", Band.PASS,
          "off-casino", Band.REJECT,
          "off-airdrop", Band.REJECT,
          "off-cheap-flights", Band.REJECT);

  private record Fixture(String label, float[] embedding) {}

  private record FixtureFile(String model, String captured, String note, List<Fixture> fixtures) {}

  @Test
  void recipeFixturesFallIntoCalibratedBands() throws IOException {
    List<Fixture> fixtures = loadFixtures();
    assertEquals(
        EXPECTED_BANDS.size(),
        fixtures.size(),
        "recipe fixture corpus changed; update EXPECTED_BANDS to match");

    EmbeddingRelevanceScorer.Centroids centroids = EmbeddingRelevanceScorer.loadCentroids();
    FilterProperties properties = new FilterProperties();
    EmbeddingRelevanceScorer scorer =
        new EmbeddingRelevanceScorer(
            centroids.recipeOnTopic(),
            centroids.recipeOffTopic(),
            centroids.recipeMean(),
            properties.getRecipeAcceptThreshold(),
            properties.getRecipeRejectThreshold());

    for (Fixture fixture : fixtures) {
      Band expected = EXPECTED_BANDS.get(fixture.label());
      assertNotNull(expected, "unexpected recipe fixture label: " + fixture.label());
      RelevanceScore result = scorer.score(fixture.embedding());
      assertEquals(
          expected,
          result.band(),
          "recipe fixture "
              + fixture.label()
              + " scored "
              + result.score()
              + "; expected "
              + expected
              + " under recipe accept="
              + properties.getRecipeAcceptThreshold()
              + " reject="
              + properties.getRecipeRejectThreshold());
    }
  }

  /**
   * The measured band, asserted as numbers. Retuning the thresholds without re-measuring the corpus
   * is exactly what this guards: both values must stay inside the gap between the strongest
   * off-topic sample and the weakest on-topic one.
   */
  @Test
  void thresholdsStayInsideTheMeasuredBand() {
    FilterProperties properties = new FilterProperties();

    assertEquals(0.60, properties.getRecipeAcceptThreshold(), 1e-9);
    assertEquals(0.38, properties.getRecipeRejectThreshold(), 1e-9);
    assertTrue(
        properties.getRecipeAcceptThreshold() <= LOWEST_ON_TOPIC_SCORE,
        "recipe accept threshold above the weakest on-topic sample would flag real recipes");
    assertTrue(
        properties.getRecipeRejectThreshold() > HIGHEST_OFF_TOPIC_SCORE,
        "recipe reject threshold at or below the strongest off-topic sample would let it pass");
    assertTrue(
        properties.getRecipeRejectThreshold() < properties.getRecipeAcceptThreshold(),
        "reject threshold must stay below the accept threshold");
  }

  private static List<Fixture> loadFixtures() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    try (InputStream in =
        RecipeRelevanceRegressionTest.class.getResourceAsStream("/filter/recipe-fixtures.json")) {
      assertNotNull(in, "recipe-fixtures.json must be on the test classpath");
      FixtureFile file = mapper.readValue(in, FixtureFile.class);
      assertNotNull(file.fixtures(), "recipe fixture file has no fixtures");
      return file.fixtures();
    }
  }
}
