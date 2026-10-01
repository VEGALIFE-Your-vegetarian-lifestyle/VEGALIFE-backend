package com.vegalife.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Cosine-similarity relevance scorer against two committed centroids (on-topic vs off-topic).
 * Before comparing, the text embedding and both centroids are mean-centered over the seed corpus:
 * raw e5 embeddings are anisotropic, so without centering the margin score is confined to a narrow
 * band around 0.5 and the accept/reject thresholds are unreachable (every post landed
 * NEEDS_REVIEW). Score = (cos(text - mean, onTopic - mean) - cos(text - mean, offTopic - mean) + 1)
 * / 2, clamped to [0, 1]; the band comes from the {@code app.filter.accept-threshold} / {@code
 * app.filter.reject-threshold} values (FR-006, FR-007). The centroids and the mean are precomputed
 * from the bilingual seed corpora in {@code src/main/resources/filter/seed-*.txt} and regenerated
 * by {@code CentroidRegenerationTest} when {@code HF_TOKEN} is available.
 */
@Component
public class EmbeddingRelevanceScorer implements RelevanceScorer {

  public static final String CENTROIDS_RESOURCE = "/filter/centroids.json";

  /** On-topic centroid with the corpus mean subtracted; pre-centered once at construction. */
  private final float[] onTopic;

  /** Off-topic centroid with the corpus mean subtracted; pre-centered once at construction. */
  private final float[] offTopic;

  private final float[] mean;
  private final double acceptThreshold;
  private final double rejectThreshold;

  /**
   * Spring wiring: loads the committed centroids and binds thresholds from {@code app.filter}.
   * Explicitly annotated because the public vector constructor below exists for tests.
   */
  @Autowired
  public EmbeddingRelevanceScorer(FilterProperties properties) {
    Centroids centroids = loadCentroids();
    validate(
        centroids.onTopic(),
        centroids.offTopic(),
        centroids.mean(),
        properties.getAcceptThreshold(),
        properties.getRejectThreshold());
    this.onTopic = subtract(centroids.onTopic(), centroids.mean());
    this.offTopic = subtract(centroids.offTopic(), centroids.mean());
    this.mean = centroids.mean();
    this.acceptThreshold = properties.getAcceptThreshold();
    this.rejectThreshold = properties.getRejectThreshold();
  }

  /**
   * Direct construction with explicit centroids, corpus mean, and thresholds (used by unit tests).
   * Both centroids are centered with the mean at construction; every scored embedding is centered
   * the same way before the cosine comparison.
   *
   * @param onTopic on-topic centroid vector
   * @param offTopic off-topic centroid vector, same length as {@code onTopic}
   * @param mean arithmetic mean of the seed corpus, same length as {@code onTopic}
   * @param acceptThreshold score >= acceptThreshold is PASS
   * @param rejectThreshold score < rejectThreshold is REJECT; must be below the accept threshold
   */
  public EmbeddingRelevanceScorer(
      float[] onTopic,
      float[] offTopic,
      float[] mean,
      double acceptThreshold,
      double rejectThreshold) {
    validate(onTopic, offTopic, mean, acceptThreshold, rejectThreshold);
    this.onTopic = subtract(onTopic, mean);
    this.offTopic = subtract(offTopic, mean);
    this.mean = mean;
    this.acceptThreshold = acceptThreshold;
    this.rejectThreshold = rejectThreshold;
  }

  @Override
  public RelevanceScore score(float[] embedding) {
    if (embedding == null || embedding.length != onTopic.length) {
      throw new IllegalArgumentException(
          "embedding length "
              + (embedding == null ? "null" : embedding.length)
              + " does not match centroid dimensions "
              + onTopic.length);
    }
    float[] centered = subtract(embedding, mean);
    double cosineOn = cosine(centered, onTopic);
    double cosineOff = cosine(centered, offTopic);
    double score = Math.max(0, Math.min(1, (cosineOn - cosineOff + 1) / 2));
    RelevanceScore.Band band =
        score >= acceptThreshold
            ? RelevanceScore.Band.PASS
            : score < rejectThreshold ? RelevanceScore.Band.REJECT : RelevanceScore.Band.REVIEW;
    return new RelevanceScore(score, band);
  }

  /**
   * Standard cosine similarity between two equal-length vectors; 0 when either vector is
   * zero-length in magnitude.
   */
  public static double cosine(float[] a, float[] b) {
    if (a.length != b.length) {
      throw new IllegalArgumentException("vectors must have equal length");
    }
    double dot = 0;
    double normA = 0;
    double normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }
    if (normA == 0 || normB == 0) {
      return 0;
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  /** Element-wise subtraction; both vectors must have equal length (callers validate). */
  private static float[] subtract(float[] vector, float[] reference) {
    float[] result = new float[vector.length];
    for (int i = 0; i < vector.length; i++) {
      result[i] = vector[i] - reference[i];
    }
    return result;
  }

  /**
   * The committed centroid set loaded from {@link #CENTROIDS_RESOURCE}.
   *
   * @param dimensions embedding dimensionality all vectors must have
   * @param onTopic normalized on-topic centroid
   * @param offTopic normalized off-topic centroid
   * @param mean arithmetic mean of every seed embedding, used to center text and centroids
   */
  public record Centroids(int dimensions, float[] onTopic, float[] offTopic, float[] mean) {}

  /** Loads and validates the committed centroids JSON from the classpath. */
  public static Centroids loadCentroids() {
    try (InputStream in = EmbeddingRelevanceScorer.class.getResourceAsStream(CENTROIDS_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing filter centroids: " + CENTROIDS_RESOURCE);
      }
      Centroids centroids = new ObjectMapper().readValue(in, Centroids.class);
      if (centroids.dimensions() <= 0
          || centroids.onTopic() == null
          || centroids.offTopic() == null
          || centroids.onTopic().length != centroids.dimensions()
          || centroids.offTopic().length != centroids.dimensions()) {
        throw new IllegalStateException("Invalid filter centroids: " + CENTROIDS_RESOURCE);
      }
      if (centroids.mean() == null || centroids.mean().length != centroids.dimensions()) {
        throw new IllegalStateException(
            "Filter centroids lack a mean vector; regenerate with CentroidRegenerationTest: "
                + CENTROIDS_RESOURCE);
      }
      return centroids;
    } catch (IOException e) {
      throw new IllegalStateException("Unreadable filter centroids: " + CENTROIDS_RESOURCE, e);
    }
  }

  private static void validate(
      float[] onTopic,
      float[] offTopic,
      float[] mean,
      double acceptThreshold,
      double rejectThreshold) {
    if (onTopic == null
        || offTopic == null
        || onTopic.length == 0
        || onTopic.length != offTopic.length) {
      throw new IllegalStateException("centroids must be non-empty and of equal length");
    }
    if (mean == null || mean.length != onTopic.length) {
      throw new IllegalStateException("mean must be non-null and match the centroid length");
    }
    if (!(rejectThreshold >= 0 && acceptThreshold <= 1 && rejectThreshold < acceptThreshold)) {
      throw new IllegalStateException(
          "thresholds must satisfy 0 <= reject < accept <= 1, got reject="
              + rejectThreshold
              + " accept="
              + acceptThreshold);
    }
  }
}
