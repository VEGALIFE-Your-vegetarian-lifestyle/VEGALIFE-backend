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
 * The retained HuggingFace {@link EmbeddingModel}, active only when {@code
 * app.filter.embedding.provider=hf} (ADR-014 decision 5).
 *
 * <p>Kept compilable and re-armable rather than deleted: the provider became unusable because the
 * account holds no Inference Providers credits, not because the integration was wrong. Restoring it
 * is a configuration change, not a code change.
 *
 * <p>When active it is the {@code @Primary} embedding bean, so callers are unaffected.
 *
 * <p>Builds its own {@link RestClient} rather than injecting a {@code RestClient.Builder}, so the
 * configuration is self-contained: it declares exactly the beans it needs, which is also what makes
 * the provider switch testable without booting the whole application.
 */
@Configuration
@Conditional(EmbeddingProviderCondition.Hf.class)
@EnableConfigurationProperties(HfEmbeddingProperties.class)
public class HfEmbeddingConfiguration {

  @Bean
  @Primary
  public EmbeddingModel hfEmbeddingModel(HfEmbeddingProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.getTimeout());
    requestFactory.setReadTimeout(properties.getTimeout());
    RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
    return new HfEmbeddingModel(restClient, properties);
  }
}
