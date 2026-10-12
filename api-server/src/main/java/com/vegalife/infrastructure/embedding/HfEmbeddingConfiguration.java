package com.vegalife.infrastructure.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Primary {@link EmbeddingModel}: a {@link RestClient} against the HuggingFace feature-extraction
 * route with explicit connect/read timeouts (ADR-007). Everything in this codebase injects this
 * bean; the Spring AI starter's auto-configured model is only kept for fallback compatibility.
 */
@Configuration
public class HfEmbeddingConfiguration {

  @Bean
  @Primary
  public EmbeddingModel hfEmbeddingModel(
      RestClient.Builder restClientBuilder, HfEmbeddingProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.getTimeout());
    requestFactory.setReadTimeout(properties.getTimeout());
    RestClient restClient = restClientBuilder.requestFactory(requestFactory).build();
    return new HfEmbeddingModel(restClient, properties);
  }
}
