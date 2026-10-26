package com.vegalife.infrastructure.google;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google sign-in client tuning, bound from {@code app.auth.google.*} (ADR-017).
 *
 * <p>{@code clientSecret} and every token Google hands back are secrets: never log them, never echo
 * them in an error body (NFR-SEC-001).
 */
@ConfigurationProperties(prefix = "app.auth.google")
@Data
public class GoogleOAuthProperties {

  /** Module's own origin for the token endpoint; overridable for tests and for a Google change. */
  private String tokenUrl = "https://oauth2.googleapis.com/token";

  /** Google's JWKS document, used to verify ID token signatures. */
  private String jwksUrl = "https://www.googleapis.com/oauth2/v3/certs";

  /** OAuth client id registered for the Vegalife clients; also the required {@code aud} claim. */
  private String clientId = "";

  /** OAuth client secret. Must never be logged. */
  private String clientSecret = "";

  /** Redirect URI the client used when obtaining the code; must match the one sent to Google. */
  private String redirectUri = "";

  /** Accepted ID token issuers. Google emits both spellings. */
  private String issuer = "https://accounts.google.com";

  private String alternateIssuer = "accounts.google.com";

  /** Two-sided lifespan cap; Google's tokens are valid for one hour. */
  private Duration clockSkew = Duration.ofSeconds(60);

  private Duration timeout = Duration.ofSeconds(10);

  /**
   * How long a fetched JWK set is reused before it is fetched again. Google rotates signing keys
   * roughly daily and a refresh is also forced whenever a token names an unknown {@code kid}.
   */
  private Duration jwksCacheTtl = Duration.ofHours(6);
}
