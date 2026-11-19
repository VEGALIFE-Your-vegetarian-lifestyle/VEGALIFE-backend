package com.vegalife.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Time-series payload for the admin console — the only dashboard surface that carries a time
 * filter. All four series share one {@code interval}, so their x-axes line up and a client can draw
 * them against a single time scale.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardChartsResponse {

  private DashboardChartsMetaResponse meta;
  private RevenueTrendResponse revenueTrend;
  private RevenueByPlanResponse revenueByPlan;
  private PaymentHealthResponse paymentHealth;
  private GrowthResponse growth;
}
