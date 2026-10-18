package com.vegalife.infrastructure.embedding;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retained HuggingFace feature-extraction client tuning, bound from {@code
 * app.filter.embedding.hf.*} (ADR-007, retained unwired by ADR-014).
 *
 * <p>These {@code HF_*} names describe a dormant provider. They are never presented as active
 * configuration; setting {@code app.filter.embedding.provider=hf} is what makes them meaningful.
 *
 * <p>Registered by {@link HfEmbeddingConfiguration} rather than picked up by component scanning, so
 * nothing binds it unless the HuggingFace client is actually selected.
 */
@Data
@ConfigurationProperties(prefix = "app.filter.embedding.hf")
public class HfEmbeddingProperties {

  private String baseUrl = "https://router.huggingface.co/hf-inference/models";

  private String model = "intfloat/multilingual-e5-large-instruct";

  private String apiKey = "";

  private Duration timeout = Duration.ofSeconds(30);
}
