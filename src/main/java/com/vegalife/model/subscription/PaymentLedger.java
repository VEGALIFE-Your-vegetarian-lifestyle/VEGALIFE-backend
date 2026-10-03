package com.vegalife.model.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "payment_ledger")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLedger {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  @Column(name = "amount", nullable = false)
  private long amount;

  @Column(name = "currency", length = 3, nullable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private Status status;

  @Column(name = "provider", length = 30)
  private String provider;

  @Column(name = "provider_reference", columnDefinition = "TEXT")
  private String providerReference;

  @Column(name = "paid_at")
  private Instant paidAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public enum Status {
    pending,
    succeeded,
    failed,
    refunded
  }
}
