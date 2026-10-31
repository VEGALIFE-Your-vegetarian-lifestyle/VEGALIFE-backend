package com.vegalife.service.filter;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Identifies the active embedding model, for the {@code model} column on {@code post_embedding}
 * (ADR-015).
 *
 * <p>Stored vectors are only comparable to vectors from the same model. The active model therefore
 * has to be recorded at write time and matched at read time, or a provider change leaves old rows
 * in one vector space being compared against new rows in another — producing similarities that look
 * plausible and mean nothing, while every health check stays green.
 *
 * <p>The name is read from the active provider's configured model rather than from a constant, so
 * it cannot drift from what the client actually calls. It is read from the {@link Environment}, not
 * from a properties bean, because only the selected provider's properties bean exists: injecting
 * the HuggingFace one would fail whenever Gemini is active, which is the default.
 */
@Component
public class ActiveEmbeddingModel {

  static final String PROVIDER_PROPERTY = "app.filter.embedding.provider";
  static final String GEMINI_MODEL_PROPERTY = "app.filter.embedding.gemini.model";
  static final String HF_MODEL_PROPERTY = "app.filter.embedding.hf.model";

  private final String name;

  public ActiveEmbeddingModel(Environment environment) {
    String provider = environment.getProperty(PROVIDER_PROPERTY, "gemini");
    boolean hf = "hf".equalsIgnoreCase(provider == null ? "" : provider.trim());
    String property = hf ? HF_MODEL_PROPERTY : GEMINI_MODEL_PROPERTY;
    String fallback = hf ? "intfloat/multilingual-e5-large-instruct" : "gemini-embedding-001";
    this.name = environment.getProperty(property, fallback);
  }

  /** The model name to record alongside a stored vector. */
  public String name() {
    return name;
  }
}
