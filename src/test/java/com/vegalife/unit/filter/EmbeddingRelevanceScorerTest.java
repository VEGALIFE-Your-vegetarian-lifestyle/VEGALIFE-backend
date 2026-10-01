package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vegalife.filter.EmbeddingRelevanceScorer;
import com.vegalife.filter.EmbeddingRelevanceScorer.Centroids;
import com.vegalife.filter.RelevanceScore;
import com.vegalife.filter.RelevanceScore.Band;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EmbeddingRelevanceScorer}: cosine band mapping, exact threshold boundaries
 * (BR-FILTER-004), input validation, and a token-free sanity check that the committed centroids
 * load and separate on-topic from off-topic content.
 */
class EmbeddingRelevanceScorerTest {

  private static final float[] ON_TOPIC = {1f, 0f};
  private static final float[] OFF_TOPIC = {0f, 1f};
  private static final float[] ZERO_MEAN = {0f, 0f};

  private static EmbeddingRelevanceScorer scorer(double accept, double reject) {
    return new EmbeddingRelevanceScorer(ON_TOPIC, OFF_TOPIC, ZERO_MEAN, accept, reject);
  }

  @Test
  void onTopicVectorScoresOneAtAcceptBoundaryAndPasses() {
    RelevanceScore result = scorer(1.0, 0.0).score(ON_TOPIC);
    assertEquals(1.0, result.score(), 1e-9);
    assertEquals(Band.PASS, result.band());
  }

  @Test
  void equalCosinesScoreExactlyHalfAndStayReviewAtRejectBoundary() {
    RelevanceScore result = scorer(0.75, 0.5).score(new float[] {1f, 1f});
    assertEquals(0.5, result.score(), 1e-9);
    assertEquals(Band.REVIEW, result.band());
  }

  @Test
  void offTopicVectorScoresZeroAndRejects() {
    RelevanceScore result = scorer(0.75, 0.45).score(OFF_TOPIC);
    assertEquals(0.0, result.score(), 1e-9);
    assertEquals(Band.REJECT, result.band());
  }

  @Test
  void midBandIsReviewBetweenExplicitThresholds() {
    RelevanceScore result = scorer(0.75, 0.45).score(new float[] {1f, 1f});
    assertEquals(0.5, result.score(), 1e-9);
    assertEquals(Band.REVIEW, result.band());
  }

  @Test
  void rejectsInvertedThresholds() {
    assertThrows(IllegalStateException.class, () -> scorer(0.45, 0.75));
  }

  @Test
  void rejectsEqualThresholds() {
    assertThrows(IllegalStateException.class, () -> scorer(0.5, 0.5));
  }

  @Test
  void rejectsMismatchedCentroidLengths() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new EmbeddingRelevanceScorer(
                new float[] {1f, 0f}, new float[] {0f, 1f, 0.5f}, ZERO_MEAN, 0.75, 0.45));
  }

  @Test
  void rejectsEmbeddingDimensionMismatch() {
    EmbeddingRelevanceScorer scorer = scorer(0.75, 0.45);
    assertThrows(IllegalArgumentException.class, () -> scorer.score(new float[] {1f}));
  }

  @Test
  void committedCentroidsLoadAndSeparateOnTopicFromOffTopic() {
    Centroids centroids = EmbeddingRelevanceScorer.loadCentroids();
    assertEquals(1024, centroids.dimensions());
    assertEquals(1024, centroids.onTopic().length);
    assertEquals(1024, centroids.offTopic().length);
    assertEquals(1024, centroids.mean().length);

    EmbeddingRelevanceScorer scorer =
        new EmbeddingRelevanceScorer(
            centroids.onTopic(), centroids.offTopic(), centroids.mean(), 0.65, 0.43);
    double onScore = scorer.score(centroids.onTopic()).score();
    double offScore = scorer.score(centroids.offTopic()).score();
    assertTrue(onScore > offScore, "on-topic centroid must score higher than off-topic");
  }
}
