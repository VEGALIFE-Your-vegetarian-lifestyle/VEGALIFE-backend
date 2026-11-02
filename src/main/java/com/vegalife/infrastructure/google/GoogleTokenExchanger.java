package com.vegalife.infrastructure.google;

import com.vegalife.shared.exception.GoogleAuthException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Exchanges a Google authorization code for an ID token (ADR-017 step 1).
 *
 * <p>The exchange happens server-side so the client secret never reaches a browser, and so the
 * token handed to the verifier was minted for this client id — nothing the caller could forge.
 */
@Slf4j
public class GoogleTokenExchanger {

  private final RestClient restClient;
  private final GoogleOAuthProperties properties;

  public GoogleTokenExchanger(RestClient restClient, GoogleOAuthProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  /**
   * @param code the authorization code the client obtained from Google
   * @return the raw {@code id_token} JWS, still unverified
   * @throws GoogleAuthException when Google rejects the code or the response is unusable
   */
  public String exchangeForIdToken(String code) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", code);
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());
    form.add("grant_type", "authorization_code");
    if (!properties.getRedirectUri().isBlank()) {
      form.add("redirect_uri", properties.getRedirectUri());
    }

    Map<?, ?> body;
    try {
      body =
          restClient
              .post()
              .uri(properties.getTokenUrl())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(Map.class);
    } catch (RuntimeException e) {
      // The upstream body may quote the code or the secret; log the type, not the payload.
      log.warn("Google token exchange failed: {}", e.getClass().getSimpleName());
      throw new GoogleAuthException("Google sign-in failed. Please try again.", e);
    }

    Object idToken = body == null ? null : body.get("id_token");
    if (!(idToken instanceof String idTokenValue) || idTokenValue.isBlank()) {
      log.warn("Google token exchange returned no id_token (keys: {})", describeKeys(body));
      throw new GoogleAuthException("Google sign-in failed. Please try again.");
    }
    return idTokenValue;
  }

  /** Maps only the parameter names a client could ever send back, for the logs. */
  private static String describeKeys(Map<?, ?> body) {
    if (body == null) {
      return "none";
    }
    return body.keySet().stream()
        .map(String::valueOf)
        .sorted()
        .map(URLEncoder::encode)
        .collect(Collectors.joining(","));
  }
}
