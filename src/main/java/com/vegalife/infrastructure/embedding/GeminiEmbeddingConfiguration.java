package com.vegalife.infrastructure.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The active {@link EmbeddingModel}: Gemini by default, chosen by {@code
 * app.filter.embedding.provider} (ADR-014).
 *
 * <p>Exactly one embedding bean is {@code @Primary} at a time, so callers that inject {@link
 * EmbeddingModel} never need to change. Selecting {@code hf} activates {@link
 * HfEmbeddingConfiguration} instead.
 */
@Configuration
@EnableConfigurationProperties(GeminiEmbeddingProperties.class)
@Conditional(EmbeddingProviderCondition.Gemini.class)
public class GeminiEmbeddingConfiguration {

  @Bean
  @Primary
  public EmbeddingModel geminiEmbeddingModel(GeminiEmbeddingProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.getTimeout());
    requestFactory.setReadTimeout(properties.getTimeout());
    RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
    return new GeminiEmbeddingModel(restClient, properties);
  }
}
