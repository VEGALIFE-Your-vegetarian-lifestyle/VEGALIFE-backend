package com.vegalife.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.infrastructure.embedding.GeminiEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Asserts that the real application wiring selects the Gemini client (ADR-014).
 *
 * <p>{@link VegalifeApplicationTest} only proves the context loads. That is not enough here: the
 * Spring AI starter also contributes an {@code EmbeddingModel}, so a provider switch that quietly
 * registered nothing would still boot cleanly — with the wrong client wired in. This test pins
 * which bean actually wins.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmbeddingProviderWiringTest {

  @Autowired private ApplicationContext context;

  @Test
  void realApplicationWiresTheGeminiClientAsThePrimaryEmbeddingModel() {
    EmbeddingModel model = context.getBean(EmbeddingModel.class);

    assertThat(model)
        .as("app.filter.embedding.provider defaults to gemini; the starter's model must not win")
        .isInstanceOf(GeminiEmbeddingModel.class);
    assertThat(model.dimensions())
        .as("must match the vector column width order 5 will create")
        .isEqualTo(768);
  }
}
