package com.vegalife.filter;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bound configuration for the content filter (FR-007): relevance thresholds and the maximum age of
 * a pending flag before the sweep re-queues it. The nested {@code app.filter.embedding.*} keys are
 * intentionally ignored here; they bind to {@link
 * com.vegalife.infrastructure.embedding.HfEmbeddingProperties}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.filter")
public class FilterProperties {

  private double acceptThreshold = 0.75;
  private double rejectThreshold = 0.45;
  private Duration sweepMaxAge = Duration.ofHours(24);
}
