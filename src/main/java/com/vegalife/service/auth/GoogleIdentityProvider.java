package com.vegalife.service.auth;

/**
 * Resolves a Google authorization code to the verified identity behind it (ADR-017, ADR-018).
 *
 * <p>This is the service layer's port: it names what sign-in needs from Google without naming any
 * Google client, HTTP route or token format, so {@link GoogleAuthService} can stay free of the
 * infrastructure packages. The exchange-then-verify sequence (get an ID token, prove it was minted
 * for this deployment, refuse an unverified email) is an implementation detail of the adapter and
 * is unchanged from what the service used to do inline.
 */
public interface GoogleIdentityProvider {

  /**
   * The verified facts a Google sign-in may act on.
   *
   * @param subject Google's stable account identifier ({@code sub}) — the linking key, never the
   *     email
   * @param email the address Google asserts, used only to find a candidate account on the first
   *     link
   * @param emailVerified whether Google has proven the user controls {@code email}
   * @param name display name, for future profile seeding; unused today
   */
  record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {}

  /**
   * @param authorizationCode the code the client obtained from Google Identity Services
   * @return the verified identity carried by that code
   * @throws com.vegalife.shared.exception.GoogleAuthException if Google rejects the code, or the
   *     token it yields fails signature, issuer, audience, expiry or verified-email checks
   */
  GoogleIdentity resolve(String authorizationCode);
}
