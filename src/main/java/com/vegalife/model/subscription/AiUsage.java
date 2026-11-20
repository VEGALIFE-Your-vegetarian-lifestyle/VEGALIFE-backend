package com.vegalife.model.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lifetime AI consumption for one member (issue #181, ADR-021).
 *
 * <p>Holds one number and nothing else: how many AI requests this member has ever consumed against
 * a granted allowance. It is never exposed by the API — the only usage number a caller sees is
 * {@code remainingUsage}, which also needs the allowance from {@code ai_plan} — and it is never
 * reset, because allowances no longer expire.
 */
@Entity
@Table(name = "ai_usage", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "request_count", nullable = false)
  private int requestCount;
}
