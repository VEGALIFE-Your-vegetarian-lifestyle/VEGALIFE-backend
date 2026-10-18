package com.vegalife.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.infrastructure.embedding.GeminiEmbeddingConfiguration;
import com.vegalife.infrastructure.embedding.GeminiEmbeddingModel;
import com.vegalife.infrastructure.embedding.HfEmbeddingConfiguration;
import com.vegalife.infrastructure.embedding.HfEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Proves the provider switch (ADR-014 decisions 4 and 5): exactly one embedding client registers,
 * HuggingFace stays re-armable, and a bad value aborts startup instead of silently leaving the
 * application with no usable client.
 *
 * <p>Uses {@link ApplicationContextRunner} rather than {@code @SpringBootTest} so the property can
 * take each value in turn without booting the whole application three times.
 */
class EmbeddingProviderSwitchTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GeminiEmbeddingConfiguration.class, HfEmbeddingConfiguration.class));

  @Test
  void defaultsToGeminiWhenThePropertyIsAbsent() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(EmbeddingModel.class);
          assertThat(context.getBean(EmbeddingModel.class))
              .isInstanceOf(GeminiEmbeddingModel.class);
        });
  }

  @Test
  void geminiIsSelectedExplicitly() {
    runner
        .withPropertyValues("app.filter.embedding.provider=gemini")
        .run(
            context ->
                assertThat(context.getBean(EmbeddingModel.class))
                    .isInstanceOf(GeminiEmbeddingModel.class));
  }

  /**
   * The HuggingFace client must remain reachable by configuration alone, per ADR-014 decision 5.
   */
  @Test
  void hfIsReArmableByConfiguration() {
    runner
        .withPropertyValues("app.filter.embedding.provider=hf")
        .run(
            context -> {
              assertThat(context).hasSingleBean(EmbeddingModel.class);
              assertThat(context.getBean(EmbeddingModel.class))
                  .isInstanceOf(HfEmbeddingModel.class);
            });
  }

  @Test
  void providerValueIsCaseAndWhitespaceInsensitive() {
    runner
        .withPropertyValues("app.filter.embedding.provider=  HF ")
        .run(
            context ->
                assertThat(context.getBean(EmbeddingModel.class))
                    .isInstanceOf(HfEmbeddingModel.class));
  }

  /**
   * The reason this is a {@code SpringBootCondition} rather than {@code @ConditionalOnProperty}: an
   * unrecognised value must fail loudly, not register nothing.
   *
   * <p>Spring wraps a condition failure in its own {@code IllegalStateException}, so the meaningful
   * message lives further down the cause chain; the assertion walks it rather than assuming the
   * top-level throwable carries it.
   */
  @Test
  void unknownProviderAbortsStartupNamingTheValidValues() {
    runner
        .withPropertyValues("app.filter.embedding.provider=openai")
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              assertThat(failure).isNotNull();
              assertThat(causeChainMessages(failure))
                  .anySatisfy(
                      message ->
                          assertThat(message)
                              .contains("app.filter.embedding.provider")
                              .contains("gemini")
                              .contains("hf"));
            });
  }

  private static java.util.List<String> causeChainMessages(Throwable failure) {
    java.util.List<String> messages = new java.util.ArrayList<>();
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current.getMessage() != null) {
        messages.add(current.getMessage());
      }
    }
    return messages;
  }
}
