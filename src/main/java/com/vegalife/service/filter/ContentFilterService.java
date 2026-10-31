package com.vegalife.service.filter;

import com.vegalife.enums.post.PostFlag;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Runs the filter pipeline for one post body: semantic relevance scoring against the committed
 * centroids (FR-005/FR-006). Maps the resulting band to the post flag: PASS → PASSED, REJECT →
 * REJECTED, REVIEW → NEEDS_REVIEW, carrying the score and reason codes. Never returns {@link
 * PostFlag#PENDING}; embedding failures propagate so the queue layer can retry.
 */
@Service
public class ContentFilterService {

  private final EmbeddingModel embeddingModel;
  private final RelevanceScorer relevanceScorer;

  public ContentFilterService(EmbeddingModel embeddingModel, RelevanceScorer relevanceScorer) {
    this.embeddingModel = embeddingModel;
    this.relevanceScorer = relevanceScorer;
  }

  /**
   * Filters a single content body and returns the verdict with flag, score, reasons and the
   * embedding it scored.
   *
   * <p>The embedding is returned rather than recomputed by the caller: duplicate detection needs
   * the same vector (ADR-015), and a second {@code embed()} call per publish would double the
   * provider cost for no benefit.
   *
   * @param content the post body (title/content already composed by the caller)
   * @return the verdict; never {@link PostFlag#PENDING}
   */
  public FilterResult filter(String content) {
    float[] embedding = embeddingModel.embed(content);
    RelevanceScore relevance = relevanceScorer.score(embedding);
    FilterVerdict verdict =
        switch (relevance.band()) {
          case PASS -> new FilterVerdict(PostFlag.PASSED, relevance.score(), List.of());
          case REJECT ->
              new FilterVerdict(
                  PostFlag.REJECTED,
                  relevance.score(),
                  List.of(formatReason("RELEVANCE_LOW", relevance.score())));
          case REVIEW ->
              new FilterVerdict(
                  PostFlag.NEEDS_REVIEW,
                  relevance.score(),
                  List.of(formatReason("RELEVANCE_REVIEW", relevance.score())));
        };
    return new FilterResult(verdict, embedding);
  }

  private static String formatReason(String code, double score) {
    return String.format(Locale.ROOT, "%s: %.3f", code, score);
  }

  /**
   * A verdict together with the vector it was scored from.
   *
   * <p>The embedding is the raw model output, not the mean-centered vector the scorer compares
   * with: post-to-post similarity must not carry the seed-corpus mean (ADR-015).
   */
  public record FilterResult(FilterVerdict verdict, float[] embedding) {}
}
