package com.vegalife.dto.response.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One payment-history row for both list endpoints (FR-003, FR-004; NFR-MAINT-001).
 *
 * <p>Money only (issue #181, ADR-021): {@code payment_ledger} no longer references a plan, so this
 * row cannot name one. "Which plan did this payment buy?" is answered by the acquisition history,
 * not here. The {@code userId} field is excluded from JSON while null, so member responses omit it
 * while admin responses always carry it — one DTO, both shapes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryItemResponse {

  private UUID id;
  private long amount;
  private String currency;
  private String status;
  private String provider;
  private String txnRef;
  private Instant paidAt;
  private Instant createdAt;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private UUID userId;
}
