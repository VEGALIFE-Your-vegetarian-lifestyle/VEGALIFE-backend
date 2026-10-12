package com.vegalife.infrastructure.embedding;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HuggingFace feature-extraction client tuning, bound from {@code app.filter.embedding.*}
 * (ADR-007).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.filter.embedding")
public class HfEmbeddingProperties {

  private String baseUrl = "https://router.huggingface.co/hf-inference/models";

  private String model = "intfloat/multilingual-e5-large-instruct";

  private String apiKey = "";

  private Duration timeout = Duration.ofSeconds(30);
}
