package com.vegalife.shared.openapi;

/**
 * Names of the OpenAPI security schemes the API documents. Lives in the kernel rather than beside
 * {@code OpenApiConfig} so that controllers (which annotate operations with
 * {@code @SecurityRequirement(name = ...)}) can reference them without importing a configuration
 * class (ADR-018).
 */
public final class OpenApiSchemeNames {

  /** HTTP bearer scheme carrying the JWT access token issued by {@code POST /api/auth/login}. */
  public static final String BEARER_AUTH = "bearerAuth";

  private OpenApiSchemeNames() {}
}
