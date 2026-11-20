package com.vegalife.dto.response.payment;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One payment's status for its owner (FR-007, FR-008; NFR-SEC-001, NFR-SEC-002; ADR-008).
 *
 * <p>Money only (issue #181, ADR-021): the plan no longer lives on the ledger, so this response
 * carries no plan. The payment-result page reads the acquisition history for that.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {

  private String paymentId;
  private String status;
  private long amount;
  private String currency;
  private Instant createdAt;
  private Instant paidAt;
}
