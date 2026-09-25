package com.vegalife.model.outbound;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbound_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", length = 16, nullable = false)
  private OutboundChannel channel;

  @Column(name = "recipient", length = 255, nullable = false)
  private String recipient;

  /** JSON message body; cleared when the row reaches a terminal status. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  @Builder.Default
  private OutboundStatus status = OutboundStatus.PENDING;

  /** Monotonic attempt counter, incremented when a worker claims the row. */
  @Column(name = "attempts", nullable = false)
  @Builder.Default
  private int attempts = 0;

  /** Earliest time the row may be claimed. */
  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  /** Business deadline (OTP expiry); past it the row expires without sending. */
  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "locked_by", length = 64)
  private String lockedBy;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public boolean isTerminal() {
    return status == OutboundStatus.COMPLETED
        || status == OutboundStatus.FAILED
        || status == OutboundStatus.EXPIRED;
  }
}
