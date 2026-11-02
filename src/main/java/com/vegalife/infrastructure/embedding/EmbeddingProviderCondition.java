package com.vegalife.infrastructure.embedding;

import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Selects exactly one {@link org.springframework.ai.embedding.EmbeddingModel} configuration from
 * {@code app.filter.embedding.provider} (ADR-014 decision 4).
 *
 * <p>Deliberately not {@code @ConditionalOnProperty}: that matches a value and silently registers
 * <em>nothing</em> when the value is unrecognised, which would leave the application with no
 * embedding client at all and fail only later, inside a filter run, far from the mistake. An
 * unrecognised provider must abort startup and name the valid values.
 *
 * <p>Validation lives in {@link #resolveProvider} and is reached by every subclass, so a bad value
 * fails regardless of which condition is evaluated first.
 */
public abstract class EmbeddingProviderCondition extends SpringBootCondition {

  static final String PROPERTY = "app.filter.embedding.provider";

  static final String GEMINI = "gemini";

  static final String HF = "hf";

  /** The provider value this condition matches. */
  protected abstract String provides();

  @Override
  public final ConditionOutcome getMatchOutcome(
      ConditionContext context, AnnotatedTypeMetadata metadata) {
    String provider = resolveProvider(context);
    return new ConditionOutcome(
        provides().equals(provider), PROPERTY + "=" + provider + " (want " + provides() + ")");
  }

  /**
   * Reads and validates the configured provider, defaulting to Gemini when absent.
   *
   * @throws IllegalStateException when the value is neither {@code gemini} nor {@code hf}
   */
  static String resolveProvider(ConditionContext context) {
    String raw = context.getEnvironment().getProperty(PROPERTY, GEMINI);
    String provider = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);

    if (!GEMINI.equals(provider) && !HF.equals(provider)) {
      throw new IllegalStateException(
          "Invalid "
              + PROPERTY
              + "='"
              + raw
              + "'. Valid values are '"
              + GEMINI
              + "' (default) and '"
              + HF
              + "'.");
    }
    return provider;
  }

  /** Activates when {@code provider=gemini}, including when the property is absent. */
  public static final class Gemini extends EmbeddingProviderCondition {

    @Override
    protected String provides() {
      return GEMINI;
    }
  }

  /** Activates when {@code provider=hf}, re-arming the retained HuggingFace client. */
  public static final class Hf extends EmbeddingProviderCondition {

    @Override
    protected String provides() {
      return HF;
    }
  }
}
