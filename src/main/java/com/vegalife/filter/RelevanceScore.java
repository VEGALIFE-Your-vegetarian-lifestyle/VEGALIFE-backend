package com.vegalife.filter;

/**
 * Semantic relevance score for one embedding, paired with the threshold band it falls into
 * (BR-FILTER-004).
 *
 * @param score cosine-derived score clamped to [0, 1]
 * @param band PASS / REVIEW / REJECT band derived from the configured thresholds
 */
public record RelevanceScore(double score, Band band) {

  /** Outcome band of a relevance score against the accept/reject thresholds. */
  public enum Band {
    PASS,
    REVIEW,
    REJECT
  }
}
