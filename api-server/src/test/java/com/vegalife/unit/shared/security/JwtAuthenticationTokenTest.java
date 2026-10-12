package com.vegalife.unit.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.shared.security.JwtAuthenticationToken;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class JwtAuthenticationTokenTest {

  @Test
  void unauthenticatedConstructor_holdsRawTokenAsCredentials() {
    JwtAuthenticationToken token = new JwtAuthenticationToken("raw.jwt.token");

    assertThat(token.isAuthenticated()).isFalse();
    assertThat(token.getPrincipal()).isNull();
    assertThat(token.getCredentials()).isEqualTo("raw.jwt.token");
  }

  @Test
  void authenticatedConstructor_holdsUserIdAndAuthorities() {
    UUID userId = UUID.randomUUID();
    List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    JwtAuthenticationToken token = new JwtAuthenticationToken(userId, authorities);

    assertThat(token.isAuthenticated()).isTrue();
    assertThat(token.getPrincipal()).isEqualTo(userId);
    assertThat(token.getUserId()).isEqualTo(userId);
    assertThat(token.getCredentials()).isNull();
    assertThat(token.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
  }

  @Test
  void getUserId_returnsPrincipalCastToUuid() {
    UUID userId = UUID.randomUUID();
    JwtAuthenticationToken token =
        new JwtAuthenticationToken(userId, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    assertThat(token.getUserId()).isEqualTo(userId);
  }
}
