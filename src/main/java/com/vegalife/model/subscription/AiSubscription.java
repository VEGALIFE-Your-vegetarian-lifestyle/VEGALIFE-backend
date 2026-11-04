package com.vegalife.model.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One plan acquisition by one member — the allowance record (issue #181, ADR-021).
 *
 * <p>Three states, distinguished by the two nullable columns:
 *
 * <ul>
 *   <li>{@code paymentId != null}, {@code grantedAt == null} — a checkout was started and the
 *       payment is not confirmed yet; contributes nothing to the allowance.
 *   <li>{@code paymentId != null}, {@code grantedAt != null} — purchased; the grant is live.
 *   <li>{@code paymentId == null}, {@code grantedAt != null} — claimed for free; {@code paymentId
 *       == null} is what marks a claim and is backed by a partial unique index.
 * </ul>
 *
 * <p>The table keeps its historical name. It no longer models a subscription: grants never expire
 * and there is no lifecycle.
 */
@Entity
@Table(name = "ai_subscription")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSubscription {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  /** The payment that paid for this acquisition; {@code null} when the plan was claimed. */
  @Column(name = "payment_id")
  private UUID paymentId;

  /** When the allowance became usable; {@code null} while a checkout is still open. */
  @Column(name = "granted_at")
  private Instant grantedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
