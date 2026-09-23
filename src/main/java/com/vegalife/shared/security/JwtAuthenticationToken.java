package com.vegalife.shared.security;

import java.util.Collection;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {

  private final Object principal;
  private final Object credentials;

  public JwtAuthenticationToken(String rawToken) {
    super(null);
    this.principal = null;
    this.credentials = rawToken;
    setAuthenticated(false);
  }

  public JwtAuthenticationToken(UUID userId, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = userId;
    this.credentials = null;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return credentials;
  }

  @Override
  public Object getPrincipal() {
    return principal;
  }

  public UUID getUserId() {
    return (UUID) principal;
  }
}
