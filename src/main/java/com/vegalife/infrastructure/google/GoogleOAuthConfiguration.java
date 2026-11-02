package com.vegalife.infrastructure.google;

import com.vegalife.service.auth.GoogleIdentityProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the Google sign-in client (ADR-017).
 *
 * <p>Builds its own {@link RestClient} rather than injecting a shared builder, matching {@code
 * GeminiEmbeddingConfiguration}: a 10-second timeout on an interactive login path must not be
 * inherited from, or imposed on, any other outbound client.
 */
@Configuration
@EnableConfigurationProperties(GoogleOAuthProperties.class)
public class GoogleOAuthConfiguration {

  @Bean
  public GoogleTokenExchanger googleTokenExchanger(GoogleOAuthProperties properties) {
    return new GoogleTokenExchanger(restClient(properties), properties);
  }

  @Bean
  public GoogleIdTokenVerifier googleIdTokenVerifier(GoogleOAuthProperties properties) {
    return new GoogleIdTokenVerifier(restClient(properties), properties);
  }

  @Bean
  public GoogleIdentityProvider googleIdentityProvider(
      GoogleTokenExchanger tokenExchanger, GoogleIdTokenVerifier idTokenVerifier) {
    return new GoogleIdentityProviderAdapter(tokenExchanger, idTokenVerifier);
  }

  private RestClient restClient(GoogleOAuthProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.getTimeout());
    requestFactory.setReadTimeout(properties.getTimeout());
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
