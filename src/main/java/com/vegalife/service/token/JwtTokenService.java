package com.vegalife.service.token;

import com.vegalife.model.token.RefreshToken;
import com.vegalife.model.user.User;
import com.vegalife.repository.token.BlacklistTokenRepository;
import com.vegalife.repository.token.RefreshTokenRepository;
import com.vegalife.shared.exception.ExpiredTokenException;
import com.vegalife.shared.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenService {

  @Value("${app.auth.jwt.secret}")
  private String secret;

  @Value("${app.auth.jwt.issuer:vegalife-backend}")
  private String issuer;

  @Value("${app.auth.jwt.audience:vegalife-api}")
  private String audience;

  @Value("${app.auth.access-token-expiry-minutes:15}")
  private long accessTokenExpiryMinutes;

  @Value("${app.auth.refresh-token-expiry-days:7}")
  private long refreshTokenExpiryDays;

  private final RefreshTokenRepository refreshTokenRepository;
  private final BlacklistTokenRepository blacklistTokenRepository;

  private SecretKey key;

  @PostConstruct
  public void init() {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String generateAccessToken(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(accessTokenExpiryMinutes * 60);
    String jti = UUID.randomUUID().toString();

    return Jwts.builder()
        .issuer(issuer)
        .subject(user.getId().toString())
        .audience()
        .add(audience)
        .and()
        .id(jti)
        .claim("username", user.getUsername())
        .claim("role", user.getRole().name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(key)
        .compact();
  }

  public String generateRefreshToken(User user) {
    String rawToken;
    int maxAttempts = 3;

    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      rawToken = generateSecureRandomToken();
      String tokenHash = hashToken(rawToken);

      try {
        RefreshToken refreshToken =
            RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(refreshTokenExpiryDays * 24 * 60 * 60))
                .build();

        refreshTokenRepository.save(refreshToken);
        log.debug("Generated refresh token for user: {}", user.getUsername());
        return rawToken;
      } catch (org.springframework.dao.DataIntegrityViolationException e) {
        log.debug("Token hash collision, retrying (attempt {}/{})", attempt + 1, maxAttempts);
      }
    }

    throw new InvalidTokenException(
        "Failed to generate unique refresh token after " + maxAttempts + " attempts");
  }

  private String generateSecureRandomToken() {
    SecureRandom secureRandom = new SecureRandom();
    byte[] randomBytes = new byte[32];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  private String hashToken(String token) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new InvalidTokenException("SHA-256 algorithm not available");
    }
  }

  public Claims parseAccessToken(String token) {
    try {
      Jws<Claims> jws =
          Jwts.parser()
              .verifyWith(key)
              .requireIssuer(issuer)
              .requireAudience(audience)
              .build()
              .parseSignedClaims(token);

      Claims claims = jws.getPayload();

      if (isBlacklisted(claims.getId(), claims.getIssuer())) {
        throw new InvalidTokenException("Access token has been revoked");
      }

      return claims;
    } catch (ExpiredJwtException e) {
      throw new ExpiredTokenException("Access token has expired");
    } catch (JwtException e) {
      throw new InvalidTokenException("Invalid access token: " + e.getMessage());
    }
  }

  public UUID validateAccessTokenAndGetUserId(String token) {
    Claims claims = parseAccessToken(token);
    return UUID.fromString(claims.getSubject());
  }

  public String extractRole(String token) {
    Claims claims = parseAccessToken(token);
    return claims.get("role", String.class);
  }

  public RefreshToken validateRefreshToken(String rawToken) {
    String tokenHash = hashToken(rawToken);

    RefreshToken refreshToken =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

    if (refreshToken.isRevoked()) {
      throw new InvalidTokenException("Refresh token has been revoked");
    }

    if (refreshToken.isExpired()) {
      throw new ExpiredTokenException("Refresh token has expired");
    }

    return refreshToken;
  }

  @Transactional
  public void revokeRefreshToken(String rawToken) {
    String tokenHash = hashToken(rawToken);
    refreshTokenRepository
        .findByTokenHash(tokenHash)
        .ifPresent(
            rt -> {
              rt.setRevokedAt(Instant.now());
              refreshTokenRepository.save(rt);
              log.debug("Revoked refresh token for user: {}", rt.getUser().getUsername());
            });
  }

  @Transactional
  public int revokeAllUserRefreshTokens(UUID userId) {
    Instant now = Instant.now();
    int count = refreshTokenRepository.revokeByUserId(userId, now);
    log.debug("Revoked {} refresh tokens for user: {}", count, userId);
    return count;
  }

  @Transactional
  public void blacklistAccessToken(String jti, String issuer, Instant expiresAt) {
    com.vegalife.model.token.BlacklistToken blacklistToken =
        com.vegalife.model.token.BlacklistToken.builder()
            .jti(jti)
            .issuer(issuer)
            .expiresAt(expiresAt)
            .revokedAt(Instant.now())
            .build();

    blacklistTokenRepository.save(blacklistToken);
    log.debug("Blacklisted access token: jti={}, issuer={}", jti, issuer);
  }

  public boolean isBlacklisted(String jti, String issuer) {
    return blacklistTokenRepository.existsByJtiAndIssuer(jti, issuer);
  }

  @Transactional
  public int cleanupExpiredRefreshTokens() {
    int count = refreshTokenRepository.deleteExpired(Instant.now());
    log.debug("Cleaned up {} expired refresh tokens", count);
    return count;
  }

  @Transactional
  public int cleanupExpiredBlacklistTokens() {
    int count = blacklistTokenRepository.deleteExpired(Instant.now());
    log.debug("Cleaned up {} expired blacklist tokens", count);
    return count;
  }
}
