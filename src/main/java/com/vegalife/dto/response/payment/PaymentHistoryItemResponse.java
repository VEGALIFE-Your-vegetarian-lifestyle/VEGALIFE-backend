package com.vegalife.dto.response.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vegalife.dto.response.subscription.PlanSummaryResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One payment-history row for both list endpoints (FR-003, FR-004; NFR-MAINT-001). The {@code
 * userId} field is excluded from JSON while null, so member responses omit it while admin responses
 * always carry it — one DTO, both shapes. {@code plan} reuses {@code
 * SubscriptionMapper.toPlanSummary} so it cannot drift from {@code GET /api/payments/{paymentId}}.
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
  private SubscriptionContext subscription;
  private PlanSummaryResponse plan;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private UUID userId;

  /** The subscription this payment produced, or {@code null} when it produced none. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SubscriptionContext {

    private UUID id;
    private String status;
    private Instant startedAt;
    private Instant renewalDate;
  }
}
