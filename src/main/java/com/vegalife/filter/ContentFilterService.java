package com.vegalife.filter;

import com.vegalife.model.post.Post;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Runs the full filter pipeline for one post body: static hard rules first — an embedding call is
 * skipped entirely when a static rule fails (FR-005) — then semantic relevance scoring against the
 * committed centroids. Maps the resulting band to the post flag (FR-006): PASS → PASSED, REJECT →
 * REJECTED, REVIEW → NEEDS_REVIEW, carrying the score and reason codes. Never returns {@link
 * Post.Flag#PENDING}; embedding failures propagate so the queue layer can retry.
 */
@Service
public class ContentFilterService {

  private final StaticRulesScorer staticRulesScorer;
  private final EmbeddingModel embeddingModel;
  private final RelevanceScorer relevanceScorer;

  public ContentFilterService(
      StaticRulesScorer staticRulesScorer,
      EmbeddingModel embeddingModel,
      RelevanceScorer relevanceScorer) {
    this.staticRulesScorer = staticRulesScorer;
    this.embeddingModel = embeddingModel;
    this.relevanceScorer = relevanceScorer;
  }

  /**
   * Filters a single content body and returns the verdict with flag, score and reasons.
   *
   * @param content the post body (title/content already composed by the caller)
   * @return the verdict; never {@link Post.Flag#PENDING}
   */
  public FilterVerdict filter(String content) {
    StaticRulesResult staticResult = staticRulesScorer.evaluate(content);
    if (!staticResult.passed()) {
      return new FilterVerdict(Post.Flag.REJECTED, null, staticResult.reasons());
    }

    float[] embedding = embeddingModel.embed(content);
    RelevanceScore relevance = relevanceScorer.score(embedding);
    return switch (relevance.band()) {
      case PASS -> new FilterVerdict(Post.Flag.PASSED, relevance.score(), List.of());
      case REJECT ->
          new FilterVerdict(
              Post.Flag.REJECTED,
              relevance.score(),
              List.of(formatReason("RELEVANCE_LOW", relevance.score())));
      case REVIEW ->
          new FilterVerdict(
              Post.Flag.NEEDS_REVIEW,
              relevance.score(),
              List.of(formatReason("RELEVANCE_REVIEW", relevance.score())));
    };
  }

  private static String formatReason(String code, double score) {
    return String.format(Locale.ROOT, "%s: %.3f", code, score);
  }
}
