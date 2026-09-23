package com.vegalife.model.token;

import com.vegalife.model.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "password_reset_otp")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetOtp {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "otp_hash", length = 64, nullable = false)
  private String otpHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public boolean isUsed() {
    return usedAt != null;
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isActive() {
    return !isUsed() && !isExpired();
  }
}
