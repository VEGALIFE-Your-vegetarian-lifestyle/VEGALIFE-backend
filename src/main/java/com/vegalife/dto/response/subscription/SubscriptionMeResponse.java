package com.vegalife.dto.response.subscription;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionMeResponse {

  private String tier;
  private String status;
  private Instant renewalDate;
  private SubscriptionUsageResponse usage;
  private PlanSummaryResponse currentPlan;
  private PaymentResponse latestPayment;
}
