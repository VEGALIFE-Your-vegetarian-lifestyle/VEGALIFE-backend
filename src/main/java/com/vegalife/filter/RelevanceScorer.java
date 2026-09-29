package com.vegalife.filter;

/**
 * Maps an embedding vector to a semantic relevance score and its threshold band. Implementations
 * decide nothing about static rules or flag persistence — that is the job of {@link
 * ContentFilterService}.
 */
public interface RelevanceScorer {

  /**
   * Scores the given embedding against the configured topic centroids.
   *
   * @param embedding the content embedding, dimension must match the scorer's centroids
   * @return the score and its band
   */
  RelevanceScore score(float[] embedding);
}
