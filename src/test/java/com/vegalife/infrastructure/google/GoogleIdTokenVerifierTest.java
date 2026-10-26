package com.vegalife.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vegalife.shared.exception.GoogleAuthException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Verifies the security-critical checks in {@link GoogleIdTokenVerifier} against a locally
 * generated RSA key pair, so the suite needs no network and no Google credentials.
 *
 * <p>The JWKS endpoint is never reached here: every test that expects an accepted token fails at
 * the fetch step in this class's setup (the default blank URL), which is precisely the point — an
 * accepted token in these tests proves the checks that run <em>before</em> the JWKS lookup, and a
 * rejected one proves the check that fired. The signature path itself is covered by feeding the
 * public key straight in through a subclass-free seam: {@link #tokenIssuedFor} signs with the
 * private key the verifier's cache would hold.
 */
class GoogleIdTokenVerifierTest {

  private static final String CLIENT_ID = "vegalife-test-client";
  private static final String ISSUER = "https://accounts.google.com";
  private static final String SUBJECT = "1122334455";
  private static final String EMAIL = "johndoe@gmail.com";

  private static RSAPrivateKey privateKey;
  private static RSAPublicKey publicKey;
  private static String kid;

  private GoogleOAuthProperties properties;

  @BeforeAll
  static void generateKeys() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    privateKey = (RSAPrivateKey) pair.getPrivate();
    publicKey = (RSAPublicKey) pair.getPublic();

    // The kid must match whatever the verifier caches; derive it from the same JWK representation.
    kid = Jwks.builder().key(publicKey).id("test-kid").build().getId();
  }

  @BeforeEach
  void setUp() {
    properties = new GoogleOAuthProperties();
    properties.setClientId(CLIENT_ID);
    properties.setIssuer(ISSUER);
    properties.setAlternateIssuer("accounts.google.com");
    properties.setClockSkew(Duration.ofSeconds(60));
    properties.setJwksUrl("http://127.0.0.1:1/jwks");
  }

  /** Builds a verifier whose key cache already holds the test key, bypassing the JWKS fetch. */
  private GoogleIdTokenVerifier verifierWithTestKey() {
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(RestClient.create(), properties);
    verifier.cacheKeyForTest(kid, publicKey);
    return verifier;
  }

  @Test
  void verify_validToken_returnsClaims() {
    String token = tokenIssuedFor(CLIENT_ID, ISSUER, Instant.now().plusSeconds(3600), true, EMAIL);

    GoogleUserInfo info = verifierWithTestKey().verify(token);

    assertThat(info.subject()).isEqualTo(SUBJECT);
    assertThat(info.email()).isEqualTo(EMAIL);
    assertThat(info.emailVerified()).isTrue();
  }

  @Test
  void verify_unverifiedEmail_isRefused() {
    String token = tokenIssuedFor(CLIENT_ID, ISSUER, Instant.now().plusSeconds(3600), false, EMAIL);

    assertThatThrownBy(() -> verifierWithTestKey().verify(token))
        .isInstanceOf(GoogleAuthException.class)
        .hasMessage("Google account email is not verified");
  }

  @Test
  void verify_wrongAudience_isRefused() {
    String token =
        tokenIssuedFor("another-client", ISSUER, Instant.now().plusSeconds(3600), true, EMAIL);

    assertThatThrownBy(() -> verifierWithTestKey().verify(token))
        .isInstanceOf(GoogleAuthException.class)
        .hasMessage("Google sign-in failed. Please try again.");
  }

  @Test
  void verify_wrongIssuer_isRefused() {
    String token =
        tokenIssuedFor(
            CLIENT_ID, "https://evil.example", Instant.now().plusSeconds(3600), true, EMAIL);

    assertThatThrownBy(() -> verifierWithTestKey().verify(token))
        .isInstanceOf(GoogleAuthException.class)
        .hasMessage("Google sign-in failed. Please try again.");
  }

  @Test
  void verify_alternateIssuerSpelling_isAccepted() {
    String token =
        tokenIssuedFor(
            CLIENT_ID, "accounts.google.com", Instant.now().plusSeconds(3600), true, EMAIL);

    assertThat(verifierWithTestKey().verify(token).subject()).isEqualTo(SUBJECT);
  }

  @Test
  void verify_expiredToken_isRefused() {
    String token = tokenIssuedFor(CLIENT_ID, ISSUER, Instant.now().minusSeconds(3600), true, EMAIL);

    assertThatThrownBy(() -> verifierWithTestKey().verify(token))
        .isInstanceOf(GoogleAuthException.class)
        .hasMessage("Google sign-in failed. Please try again.");
  }

  @Test
  void verify_unknownKeyId_triggersAKeySetFetchAndFails() {
    String token = tokenIssuedFor(CLIENT_ID, ISSUER, Instant.now().plusSeconds(3600), true, EMAIL);

    // No cached key, and the JWKS URL in setUp is unreachable.
    assertThatThrownBy(
            () -> new GoogleIdTokenVerifier(RestClient.create(), properties).verify(token))
        .isInstanceOf(GoogleAuthException.class);
  }

  @Test
  void verify_tamperedPayload_failsSignature() {
    String token = tokenIssuedFor(CLIENT_ID, ISSUER, Instant.now().plusSeconds(3600), true, EMAIL);
    String[] parts = token.split("\\.");
    String forgedPayload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"iss\":\"" + ISSUER + "\",\"sub\":\"attacker\",\"aud\":\"" + CLIENT_ID + "\"}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    String forged = parts[0] + "." + forgedPayload + "." + parts[2];

    assertThatThrownBy(() -> verifierWithTestKey().verify(forged))
        .isInstanceOf(GoogleAuthException.class);
  }

  private String tokenIssuedFor(
      String audience, String issuer, Instant expiry, boolean emailVerified, String email) {
    return Jwts.builder()
        .header()
        .keyId(kid)
        .and()
        .issuer(issuer)
        .subject(SUBJECT)
        .audience()
        .add(audience)
        .and()
        .claim("email", email)
        .claim("email_verified", emailVerified)
        .claim("name", "John Doe")
        .issuedAt(Date.from(Instant.now().minusSeconds(10)))
        .expiration(Date.from(expiry))
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }
}
