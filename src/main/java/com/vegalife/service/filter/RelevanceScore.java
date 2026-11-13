package com.vegalife.service.filter;

/**
 * Semantic relevance score for one embedding, paired with the threshold band it falls into
 * (BR-FILTER-004).
 *
 * @param score cosine-derived score clamped to [0, 1]
 * @param band PASS / REVIEW / REJECT band derived from the configured thresholds
 * @param threshold the accept or reject boundary that produced {@code band}: the accept threshold
 *     for PASS and REVIEW, the reject threshold for REJECT. Snapshotted into {@code flag_detail} so
 *     a stored score keeps explaining itself after an administrator retunes the filter (ADR-024).
 *     {@code null} when the caller did not know it (test doubles, synthetic scores)
 */
public record RelevanceScore(double score, Band band, Double threshold) {

  /** Convenience for callers that do not know which boundary was applied. */
  public RelevanceScore(double score, Band band) {
    this(score, band, null);
  }

  /** Outcome band of a relevance score against the accept/reject thresholds. */
  public enum Band {
    PASS,
    REVIEW,
    REJECT
  }
}
