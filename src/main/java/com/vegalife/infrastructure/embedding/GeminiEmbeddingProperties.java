package com.vegalife.infrastructure.embedding;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gemini embedding client tuning, bound from {@code app.filter.embedding.gemini.*} (ADR-014).
 *
 * <p>Prefixed with {@code EMBED_} rather than {@code HF_}: the active provider's configuration is
 * what a deployer sets, and the dormant HuggingFace block keeps its historical names so the two can
 * never be confused.
 */
@Data
@ConfigurationProperties(prefix = "app.filter.embedding.gemini")
public class GeminiEmbeddingProperties {

  private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

  private String model = "gemini-embedding-001";

  private String apiKey = "";

  /**
   * Vector width. Must stay in step with order 5's {@code vector(N)} column: a mismatch fails at
   * insert time rather than at startup, so it is worth stating here.
   */
  private int dimensions = 768;

  private Duration timeout = Duration.ofSeconds(30);

  /**
   * Inputs per HTTP request. Gemini's batch endpoint has an upper bound; this is deliberately well
   * under it so a large corpus still succeeds in ordered chunks.
   */
  private int batchSize = 100;
}
