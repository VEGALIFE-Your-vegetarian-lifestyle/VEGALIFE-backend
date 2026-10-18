package com.vegalife.infrastructure.embedding;

/**
 * The {@code task_type} every Gemini embedding call sends (ADR-014 decision 2).
 *
 * <p>Gemini applies a task-specific transformation to the text before embedding. Since the filter
 * compares a post against centroids built from the seed corpus, both sides must be transformed the
 * same way or they are not in the same space and every threshold calibrated in that space is
 * meaningless.
 *
 * <p>This lives in its own class, rather than inlined at each call site, precisely so the two paths
 * cannot drift apart: centroid regeneration and live scoring import the same constant. Asymmetric
 * types such as {@code RETRIEVAL_DOCUMENT} / {@code RETRIEVAL_QUERY} are wrong here for exactly
 * that reason.
 */
public final class GeminiTaskType {

  /** Symmetric: both the post and the centroids are passive text, neither is a query. */
  public static final String SEMANTIC_SIMILARITY = "SEMANTIC_SIMILARITY";

  private GeminiTaskType() {}
}
