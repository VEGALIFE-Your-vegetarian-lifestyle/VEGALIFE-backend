package com.vegalife.unit.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.vegalife.shared.config.SecurityConfig;
import com.vegalife.shared.security.JwtAuthenticationFilter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigCorsTest {

  private CorsConfigurationSource corsConfigurationSource;

  @BeforeEach
  void setUp() {
    SecurityConfig securityConfig = new SecurityConfig(mock(JwtAuthenticationFilter.class));
    corsConfigurationSource = securityConfig.corsConfigurationSource();
  }

  private CorsConfiguration configurationFor(String path) {
    return corsConfigurationSource.getCorsConfiguration(new MockHttpServletRequest("GET", path));
  }

  @Test
  void shouldAllowAnyOriginOnEveryPath() {
    CorsConfiguration configuration = configurationFor("/api/posts");

    assertThat(configuration).isNotNull();
    assertThat(configuration.checkOrigin("https://app.example.com"))
        .isEqualTo("https://app.example.com");
    assertThat(configuration.checkOrigin("http://localhost:5173"))
        .isEqualTo("http://localhost:5173");
  }

  @Test
  void shouldAllowAnyMethod() {
    CorsConfiguration configuration = configurationFor("/api/posts");

    assertThat(configuration).isNotNull();
    assertThat(configuration.checkHttpMethod(HttpMethod.GET)).contains(HttpMethod.GET);
    assertThat(configuration.checkHttpMethod(HttpMethod.POST)).contains(HttpMethod.POST);
    assertThat(configuration.checkHttpMethod(HttpMethod.DELETE)).contains(HttpMethod.DELETE);
  }

  @Test
  void shouldAllowAnyRequestHeader() {
    CorsConfiguration configuration = configurationFor("/api/posts");

    assertThat(configuration).isNotNull();
    assertThat(configuration.checkHeaders(List.of("Authorization", "X-Custom-Header")))
        .containsExactly("Authorization", "X-Custom-Header");
  }

  @Test
  void shouldAllowCredentialsForCredentialedFrontends() {
    CorsConfiguration configuration = configurationFor("/actuator");

    assertThat(configuration).isNotNull();
    assertThat(configuration.getAllowCredentials()).isTrue();
  }
}
