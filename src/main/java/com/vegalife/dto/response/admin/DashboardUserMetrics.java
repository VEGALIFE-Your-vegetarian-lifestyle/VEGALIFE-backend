package com.vegalife.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Account metrics. All values are lifetime or current-state — the dashboard carries no time filter;
 * anything that moves over time lives on the charts endpoint instead.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardUserMetrics {

  private long total;

  /** Accounts with {@code email_verified = true}. Set once, so it never reverts. */
  private long emailVerified;

  /**
   * Accounts that have ever consumed an AI request ({@code ai_usage.request_count > 0}). The only
   * monotonic activation signal: {@code user.status = activated} is current state and is
   * overwritten by a suspend or deactivate.
   */
  private long activatedEver;

  private long suspended;

  private long deactivated;

  /** {@code emailVerified / total}, as a fraction. Zero when there are no accounts. */
  private double activationRate;
}
