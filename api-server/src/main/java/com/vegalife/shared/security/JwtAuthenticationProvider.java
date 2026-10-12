package com.vegalife.shared.security;

import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserAccountAuthState;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import com.vegalife.shared.exception.AccountInactiveException;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationProvider implements AuthenticationProvider {

  private final JwtTokenService jwtTokenService;
  private final UserRepository userRepository;

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
      return null;
    }

    String rawToken = (String) jwtAuthentication.getCredentials();
    Claims claims = jwtTokenService.parseAccessToken(rawToken);

    UUID userId;
    try {
      userId = UUID.fromString(claims.getSubject());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new AuthenticationServiceException("Invalid access token");
    }

    UserAccountAuthState account =
        userRepository
            .findAccountAuthStateById(userId)
            .orElseThrow(() -> new AccountInactiveException("Account is not active"));

    if (account.getDeletedAt() != null) {
      throw new AccountInactiveException("Account is not active");
    }

    if (account.getStatus() != User.Status.activated) {
      throw new AccountInactiveException("Account is not active");
    }

    List<GrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()));

    log.debug("Authenticated user {} with role {}", userId, account.getRole());
    return new JwtAuthenticationToken(userId, authorities);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return JwtAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
