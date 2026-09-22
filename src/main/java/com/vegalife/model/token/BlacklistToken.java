package com.vegalife.model.token;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "blacklist_token")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(BlacklistTokenId.class)
public class BlacklistToken {

  @Id
  @Column(name = "jti", length = 36, nullable = false)
  private String jti;

  @Id
  @Column(name = "issuer", length = 100, nullable = false)
  private String issuer;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at", nullable = false)
  private Instant revokedAt;

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }
}
