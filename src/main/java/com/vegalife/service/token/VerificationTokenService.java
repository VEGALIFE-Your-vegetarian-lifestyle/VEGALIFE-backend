package com.vegalife.service.token;

import com.vegalife.model.user.User;
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
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationTokenService {

  @Value("${app.verification.secret}")
  private String secret;

  @Value("${app.verification.token-expiry-minutes:30}")
  private long expiryMinutes;

  @Value("${app.verification.issuer:vegalife-backend}")
  private String issuer;

  private SecretKey key;

  @PostConstruct
  public void init() {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String generateToken(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(expiryMinutes * 60);

    return Jwts.builder()
        .issuer(issuer)
        .subject(user.getId().toString())
        .claim("type", "EMAIL_VERIFY")
        .claim("email", user.getEmail())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(key)
        .compact();
  }

  public Claims parseToken(String token) {
    try {
      Jws<Claims> jws =
          Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token);

      Claims claims = jws.getPayload();

      if (!"EMAIL_VERIFY".equals(claims.get("type", String.class))) {
        throw new InvalidTokenException("Invalid token type");
      }

      return claims;
    } catch (ExpiredJwtException e) {
      throw new ExpiredTokenException("Verification token has expired");
    } catch (JwtException e) {
      throw new InvalidTokenException("Invalid verification token: " + e.getMessage());
    }
  }

  public UUID getUserIdFromToken(String token) {
    Claims claims = parseToken(token);
    return UUID.fromString(claims.getSubject());
  }

  public String getEmailFromToken(String token) {
    Claims claims = parseToken(token);
    return claims.get("email", String.class);
  }
}
