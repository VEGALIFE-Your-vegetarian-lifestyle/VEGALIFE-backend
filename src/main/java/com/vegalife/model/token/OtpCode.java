package com.vegalife.model.token;

import com.vegalife.model.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "otp_code")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpCode {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "otp_hash", length = 64, nullable = false)
  private String otpHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", length = 32, nullable = false)
  @Builder.Default
  private OtpPurpose purpose = OtpPurpose.PASSWORD_RESET;

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
