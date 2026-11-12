package com.vegalife.service.filter;

import java.util.Optional;

/**
 * Scores one component of a post and answers the single question the pipeline asks about it: does
 * it clear the accept threshold?
 *
 * <p>Component-level verification (ADR-016) scores two things — the post body and the recipe body —
 * against <em>different</em> centroid pairs. Rather than let each caller re-implement the banding,
 * this service holds all four threshold values ({@code app.filter.accept-threshold} / {@code
 * app.filter.reject-threshold} for content, {@code app.filter.recipe.*} for the recipe body) and
 * maps a score to a verdict. The arithmetic itself is not duplicated: {@link
 * EmbeddingRelevanceScorer} remains the only implementation of the cosine/centering formula, and is
 * constructed twice with different centroid pairs.
 *
 * <p>A component that could not be scored is reported as needing review rather than as a pass.
 * Content that nobody has looked at must not publish itself. The result deliberately does not carry
 * a {@link com.vegalife.enums.post.PostFlag} (ADR-023): a component decision is not a stored
 * reason.
 */
public interface ComponentScorer {

  /**
   * Scores one component body.
   *
   * @param component which component the text belongs to
   * @param text the exact text to embed and score
   * @return the score and whether it cleared its threshold
   */
  ComponentResult score(Component component, String text);

  /** Components that carry their own centroid pair and threshold. */
  enum Component {
    /** The post's {@code title + content}. */
    CONTENT,
    /** The attached recipe's {@code name + description + instructions}. */
    RECIPE
  }

  /**
   * Result of scoring one component.
   *
   * @param component the component scored
   * @param needsReview true when the score fell below the accept threshold or the component could
   *     not be scored
   * @param score the relevance score, or empty when the component was never scored
   * @param embedding the raw embedding, present only when a model call happened
   */
  record ComponentResult(
      Component component,
      boolean needsReview,
      Optional<Double> score,
      Optional<float[]> embedding) {

    /** A component that was deliberately not scored (no text to score, or no recipe attached). */
    public static ComponentResult notScored(Component component) {
      return new ComponentResult(component, false, Optional.empty(), Optional.empty());
    }

    /**
     * A component whose scoring failed. It is treated exactly like a low score: the post goes to a
     * human rather than failing the member's publish (ADR-016 decision D16).
     */
    public static ComponentResult failed(Component component) {
      return new ComponentResult(component, true, Optional.empty(), Optional.empty());
    }
  }
}
