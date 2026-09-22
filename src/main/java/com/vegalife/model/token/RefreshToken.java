package com.vegalife.model.token;

import com.vegalife.model.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "refresh_token")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", length = 64, nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isValid() {
    return !isRevoked() && !isExpired();
  }
}
