package com.vegalife.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

/**
 * Regression test for #85: a bare local run (no active profile, no HF_TOKEN) must resolve a
 * non-empty spring.ai.openai.api-key, otherwise the OpenAiAudioSpeechModel bean fails and startup
 * aborts. MockEnvironment carries no OS environment or system properties, so the result is
 * deterministic regardless of the machine running the test.
 */
class BareRunOpenAiApiKeyConfigTest {

  @Test
  void shouldResolveNonEmptyOpenAiApiKeyWhenNoProfileIsActive() {
    MockEnvironment environment = new MockEnvironment();

    ConfigDataEnvironmentPostProcessor.applyTo(environment);

    assertThat(environment.getProperty("spring.ai.openai.api-key")).isNotBlank();
  }
}
