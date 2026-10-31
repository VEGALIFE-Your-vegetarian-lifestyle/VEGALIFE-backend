package com.vegalife.infrastructure.google;

import com.vegalife.service.auth.GoogleIdentityProvider;

/**
 * Adapter that satisfies {@link GoogleIdentityProvider} with the two Google clients (ADR-017,
 * ADR-018): {@link GoogleTokenExchanger} swaps the authorization code for an ID token, {@link
 * GoogleIdTokenVerifier} then proves that token before anything is read from it. The refusals, log
 * lines and ordering are exactly what {@code GoogleAuthService} used to drive directly.
 */
public class GoogleIdentityProviderAdapter implements GoogleIdentityProvider {

  private final GoogleTokenExchanger tokenExchanger;
  private final GoogleIdTokenVerifier idTokenVerifier;

  public GoogleIdentityProviderAdapter(
      GoogleTokenExchanger tokenExchanger, GoogleIdTokenVerifier idTokenVerifier) {
    this.tokenExchanger = tokenExchanger;
    this.idTokenVerifier = idTokenVerifier;
  }

  @Override
  public GoogleIdentity resolve(String authorizationCode) {
    String idToken = tokenExchanger.exchangeForIdToken(authorizationCode);
    GoogleUserInfo info = idTokenVerifier.verify(idToken);
    return new GoogleIdentity(info.subject(), info.email(), info.emailVerified(), info.name());
  }
}
