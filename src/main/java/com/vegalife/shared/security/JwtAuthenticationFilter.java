package com.vegalife.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final ObjectProvider<org.springframework.security.authentication.AuthenticationManager>
      authenticationManagerProvider;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String token = extractToken(request);

    if (token != null) {
      try {
        org.springframework.security.authentication.AuthenticationManager authenticationManager =
            authenticationManagerProvider.getObject();
        JwtAuthenticationToken authenticationRequest = new JwtAuthenticationToken(token);
        Authentication authentication = authenticationManager.authenticate(authenticationRequest);
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
          jwtAuthentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (com.vegalife.shared.exception.ExpiredTokenException e) {
        log.debug("Expired access token: {}", e.getMessage());
        writeUnauthorized(response, "Access token has expired");
        return;
      } catch (com.vegalife.shared.exception.InvalidTokenException e) {
        log.debug("Invalid access token: {}", e.getMessage());
        writeUnauthorized(response, "Invalid access token");
        return;
      } catch (com.vegalife.shared.exception.AccountInactiveException e) {
        log.debug("Account inactive for request: {}", e.getMessage());
        writeUnauthorized(response, e.getMessage());
        return;
      } catch (AuthenticationException e) {
        log.debug("Authentication failed: {}", e.getMessage());
        writeUnauthorized(response, "Invalid access token");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private String extractToken(HttpServletRequest request) {
    String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
      return bearerToken.substring(BEARER_PREFIX.length());
    }
    return null;
  }

  private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.getWriter().write(message);
  }
}
