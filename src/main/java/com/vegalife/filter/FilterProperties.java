package com.vegalife.filter;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bound configuration for the content filter (FR-007): relevance thresholds and the maximum age of
 * a pending flag before the sweep re-queues it. The nested {@code app.filter.embedding.*} keys are
 * intentionally ignored here; they bind to the provider-specific properties classes (ADR-014).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.filter")
public class FilterProperties {

  private double acceptThreshold = 0.70;
  private double rejectThreshold = 0.45;

  /**
   * Accept and reject thresholds for the <strong>recipe body</strong> ({@code name + description +
   * instructions}), scored against its own centroid pair. Recipe text is a different distribution
   * from post prose — mostly imperative steps, ingredient amounts and timings — so it is calibrated
   * separately rather than reusing the content thresholds (ADR-016).
   *
   * <p>These defaults mirror the content defaults until the corpus is measured; the calibration
   * record is {@code docs/feats/filter-calibration-gemini.md}.
   */
  private double recipeAcceptThreshold = 0.70;

  private double recipeRejectThreshold = 0.45;
  private Duration sweepMaxAge = Duration.ofHours(24);

  /**
   * Cosine <strong>similarity</strong> (not distance) at or above which a post is treated as a
   * duplicate of an existing published post. Calibrated against real Vietnamese and English text;
   * see {@code docs/feats/duplicate-post-detection.md}.
   */
  private double duplicateThreshold = 0.85;

  /**
   * Turns duplicate detection off without disabling the topic filter. Detection is additive, so
   * switching it off leaves the publish path exactly as it was before the feature.
   */
  private boolean duplicateEnabled = true;

  /**
   * When false, a published post skips the semantic filter entirely and lands directly in the admin
   * review queue ({@code flag=NEEDS_REVIEW}, {@code status=unpublished}) with no embedding call.
   * True by default so the filter is the normal path; false is a deliberate outage mode.
   */
  private boolean enabled = true;
}
