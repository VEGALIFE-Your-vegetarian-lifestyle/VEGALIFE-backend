package com.vegalife.dto.response.payment;

import com.vegalife.dto.response.subscription.PlanSummaryResponse;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {

  private String paymentId;
  private String status;
  private PlanSummaryResponse plan;
  private long amount;
  private String currency;
  private Instant createdAt;
  private Instant paidAt;
}
