package com.vegalife.infrastructure.google;

import com.vegalife.shared.exception.GoogleAuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.RsaPublicJwk;
import java.security.Key;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * Verifies a Google-issued ID token before anything acts on it (ADR-017 step 1, NFR-SEC-002).
 *
 * <p>Every check is done here and failure is total: a token that fails any of them never reaches a
 * database read. In order:
 *
 * <ol>
 *   <li>signature — RS256 only, against the key whose {@code kid} the header names;
 *   <li>{@code iss} — one of Google's two spellings;
 *   <li>{@code aud} — exactly this deployment's OAuth client id (the confused-deputy guard: a token
 *       minted for somebody else's client is refused);
 *   <li>{@code exp} / {@code iat} — with a configurable clock skew;
 *   <li>{@code sub} — present, so a link can be keyed;
 *   <li>{@code email_verified} — must be literally {@code true} (BR-AUTH-022).
 * </ol>
 *
 * <p>A token naming an unknown {@code kid} triggers one forced JWKS refresh, so rotating keys do
 * not cause a sign-in outage; the refresh is not available when the set was refreshed moments ago,
 * which keeps an attacker who guesses kids from driving an outbound request per attempt.
 */
@Slf4j
public class GoogleIdTokenVerifier {

  private static final String ALGORITHM = "RS256";
  private static final long MIN_REFRESH_GAP_SECONDS = 60;

  private final RestClient restClient;
  private final GoogleOAuthProperties properties;
  private final Map<String, RSAPublicKey> keyCache = new HashMap<>();
  private volatile Instant keysFetchedAt = Instant.EPOCH;

  public GoogleIdTokenVerifier(RestClient restClient, GoogleOAuthProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  /**
   * @param idToken the raw JWS returned by the token exchange
   * @return the verified claims, as {@link GoogleUserInfo}
   * @throws GoogleAuthException when verification fails or the email is not verified
   */
  public GoogleUserInfo verify(String idToken) {
    Claims claims = parseAndVerifySignature(idToken);
    checkIssuer(claims);
    checkAudience(claims);
    checkExpiry(claims);

    String subject = claims.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new GoogleAuthException("Google sign-in failed. Please try again.");
    }

    String email = claims.get("email", String.class);
    boolean emailVerified = Boolean.TRUE.equals(claims.get("email_verified", Boolean.class));
    if (!emailVerified || email == null || email.isBlank()) {
      throw new GoogleAuthException("Google account email is not verified");
    }

    return new GoogleUserInfo(subject, email, true, claims.get("name", String.class));
  }

  private Claims parseAndVerifySignature(String idToken) {
    String kid = readKeyId(idToken);
    Key key = resolveKey(kid);

    try {
      return Jwts.parser()
          .verifyWith((java.security.PublicKey) key)
          .build()
          .parseSignedClaims(idToken)
          .getPayload();
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("Google ID token failed signature verification: {}", e.getClass().getSimpleName());
      throw new GoogleAuthException("Google sign-in failed. Please try again.", e);
    }
  }

  /** Reads {@code kid}/{@code alg} without trusting the token; parsing the body comes later. */
  private String readKeyId(String idToken) {
    int firstDot = idToken.indexOf('.');
    if (firstDot <= 0) {
      throw new GoogleAuthException("Google sign-in failed. Please try again.");
    }
    try {
      byte[] headerJson = java.util.Base64.getUrlDecoder().decode(idToken.substring(0, firstDot));
      // A Map, not JwsHeader: the interface has no Jackson creator, and the header is untrusted
      // input anyway — read exactly the two fields needed and nothing more.
      java.util.Map<?, ?> header =
          new com.fasterxml.jackson.databind.ObjectMapper()
              .readValue(headerJson, java.util.Map.class);
      Object keyId = header.get("kid");
      Object algorithm = header.get("alg");
      if (!(keyId instanceof String keyIdValue) || keyIdValue.isBlank()) {
        throw new GoogleAuthException("Google sign-in failed. Please try again.");
      }
      if (!ALGORITHM.equals(algorithm)) {
        throw new GoogleAuthException("Google sign-in failed. Please try again.");
      }
      return keyIdValue;
    } catch (java.io.IOException | IllegalArgumentException e) {
      throw new GoogleAuthException("Google sign-in failed. Please try again.", e);
    }
  }

  private Key resolveKey(String kid) {
    RSAPublicKey cached = keyCache.get(kid);
    if (cached != null && !keysStale()) {
      return cached;
    }

    synchronized (this) {
      cached = keyCache.get(kid);
      if (cached != null && !keysStale()) {
        return cached;
      }
      if (cached == null || canRefreshNow()) {
        refreshKeys();
        cached = keyCache.get(kid);
      }
    }
    if (cached == null) {
      throw new GoogleAuthException("Google sign-in failed. Please try again.");
    }
    return cached;
  }

  /**
   * Seeds the key cache directly.
   *
   * <p>Visible to the verifier's own unit test so the signature, issuer, audience, expiry and
   * {@code email_verified} checks can be exercised against a locally generated key pair without
   * reaching Google. Production code never calls this.
   */
  void cacheKeyForTest(String keyId, RSAPublicKey key) {
    keyCache.put(keyId, key);
    keysFetchedAt = Instant.now();
  }

  private boolean keysStale() {
    return Instant.now().isAfter(keysFetchedAt.plus(properties.getJwksCacheTtl()));
  }

  private boolean canRefreshNow() {
    return Instant.now().isAfter(keysFetchedAt.plus(Duration.ofSeconds(MIN_REFRESH_GAP_SECONDS)));
  }

  private void refreshKeys() {
    JwkSet jwkSet;
    try {
      String body = restClient.get().uri(properties.getJwksUrl()).retrieve().body(String.class);
      jwkSet = Jwks.setParser().build().parse(body);
    } catch (RuntimeException e) {
      log.warn("Fetching Google JWKS failed: {}", e.getClass().getSimpleName());
      if (keyCache.isEmpty()) {
        throw new GoogleAuthException("Google sign-in failed. Please try again.", e);
      }
      // Keep serving the keys already held rather than failing every sign-in on a transient blip.
      keysFetchedAt = Instant.now();
      return;
    }

    Map<String, RSAPublicKey> refreshed = new HashMap<>();
    for (Jwk<?> jwk : Optional.ofNullable(jwkSet.getKeys()).orElseGet(java.util.Set::of)) {
      if (jwk instanceof RsaPublicJwk rsa && rsa.getId() != null) {
        try {
          refreshed.put(rsa.getId(), rsa.toKey());
        } catch (RuntimeException e) {
          // A malformed key is skipped, not fatal: the remaining keys still verify real tokens.
          log.warn(
              "Skipping malformed Google JWK {}: {}", rsa.getId(), e.getClass().getSimpleName());
        }
      }
    }
    if (refreshed.isEmpty()) {
      log.warn("Google JWKS contained no usable RSA keys");
      throw new GoogleAuthException("Google sign-in failed. Please try again.");
    }
    synchronized (this) {
      keyCache.clear();
      keyCache.putAll(refreshed);
      keysFetchedAt = Instant.now();
    }
  }

  private void checkIssuer(Claims claims) {
    String issuer = claims.getIssuer();
    if (!properties.getIssuer().equals(issuer) && !properties.getAlternateIssuer().equals(issuer)) {
      log.warn("Google ID token has unexpected issuer");
      throw new GoogleAuthException("Google sign-in failed. Please try again.");
    }
  }

  private void checkAudience(Claims claims) {
    if (properties.getClientId().isBlank()
        || claims.getAudience() == null
        || !claims.getAudience().contains(properties.getClientId())) {
      log.warn("Google ID token audience does not match this client");
      throw new GoogleAuthException("Google sign-in failed. Please try again.");
    }
  }

  private void checkExpiry(Claims claims) {
    Instant now = Instant.now();
    Duration skew = properties.getClockSkew();
    Date expiration = claims.getExpiration();
    Date issuedAt = claims.getIssuedAt();
    if (expiration == null || now.minus(skew).isAfter(expiration.toInstant())) {
      log.warn("Google ID token is expired or has no expiry");
      throw new GoogleAuthException("Google sign-in failed. Please try again.");
    }
    if (issuedAt != null && now.plus(skew).isBefore(issuedAt.toInstant())) {
      log.warn("Google ID token was issued in the future");
      throw new GoogleAuthException("Google sign-in failed. Please try again.");
    }
  }
}
