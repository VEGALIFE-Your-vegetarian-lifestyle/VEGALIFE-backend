package com.vegalife.service.filter;

import com.vegalife.enums.post.PostFlag;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Default {@link ComponentScorer}: one embedding call per component, scored against that
 * component's own centroid pair.
 *
 * <p>The pair is supplied at construction so tests can hand in synthetic centroids. Production
 * wiring builds one instance per component from the committed {@code centroids.json}.
 *
 * <p>Embedding failures are caught here rather than propagated. That is a deliberate departure from
 * {@link ContentFilterService}, where a failure propagates so the outbound queue retries: at
 * component level a single unavailable provider call must not stall a member's publish, so the
 * component is recorded as unscored and the post is queued for a human instead (ADR-016 D16).
 */
@Slf4j
public class EmbeddingComponentScorer implements ComponentScorer {

  private final EmbeddingModel embeddingModel;
  private final Component component;
  private final RelevanceScorer scorer;

  public EmbeddingComponentScorer(
      EmbeddingModel embeddingModel, Component component, RelevanceScorer scorer) {
    this.embeddingModel = embeddingModel;
    this.component = component;
    this.scorer = scorer;
  }

  /** Wires the content and recipe scorers from the committed centroids and properties. */
  @Service
  public static class Factory {

    private final EmbeddingModel embeddingModel;
    private final FilterProperties properties;

    @Autowired
    public Factory(EmbeddingModel embeddingModel, FilterProperties properties) {
      this.embeddingModel = embeddingModel;
      this.properties = properties;
    }

    public ComponentScorer forComponent(Component component) {
      EmbeddingRelevanceScorer.Centroids centroids = EmbeddingRelevanceScorer.loadCentroids();
      return switch (component) {
        case CONTENT ->
            new EmbeddingComponentScorer(
                embeddingModel,
                component,
                new EmbeddingRelevanceScorer(
                    centroids.onTopic(),
                    centroids.offTopic(),
                    centroids.mean(),
                    properties.getAcceptThreshold(),
                    properties.getRejectThreshold()));
        case RECIPE ->
            new EmbeddingComponentScorer(
                embeddingModel,
                component,
                new EmbeddingRelevanceScorer(
                    centroids.recipeOnTopic(),
                    centroids.recipeOffTopic(),
                    centroids.recipeMean(),
                    properties.getRecipeAcceptThreshold(),
                    properties.getRecipeRejectThreshold()));
      };
    }
  }

  @Override
  public ComponentResult score(Component requested, String text) {
    if (requested != component) {
      throw new IllegalArgumentException("This scorer handles " + component + ", not " + requested);
    }
    if (text == null || text.isBlank()) {
      return ComponentResult.notScored(component);
    }
    float[] embedding;
    try {
      embedding = embeddingModel.embed(text);
    } catch (RuntimeException e) {
      log.warn(
          "Could not embed the {} component; the post will be queued for review instead of"
              + " failing the publish: {}",
          component,
          e.getMessage());
      return ComponentResult.failed(component);
    }
    RelevanceScore relevance = scorer.score(embedding);
    PostFlag flag =
        relevance.band() == RelevanceScore.Band.PASS ? PostFlag.PASSED : PostFlag.NEEDS_REVIEW;
    return new ComponentResult(
        component, flag, Optional.of(relevance.score()), Optional.of(embedding));
  }
}
