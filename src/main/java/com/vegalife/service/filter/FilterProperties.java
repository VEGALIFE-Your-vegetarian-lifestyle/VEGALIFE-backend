package com.vegalife.service.filter;

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
   * Fallback for the {@code content_filter_enabled} platform setting. When filtering is off, a
   * submitted post skips the semantic filter and is parked directly in the admin review queue
   * ({@code status=pending}, {@code flag=FILTER_SKIPPED}) with no embedding call (ADR-023,
   * ADR-029). True by default so the filter is the normal path; false is a deliberate outage mode.
   */
  private boolean enabled = true;
}
