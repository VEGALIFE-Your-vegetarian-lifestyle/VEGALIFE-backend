package com.vegalife.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lifetime platform snapshot for the admin console. Every block is all-time or current-state: the
 * summary carries no time filter at all. Anything that changes over time is served by the charts
 * endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

  private DashboardMetaResponse meta;
  private DashboardUserMetrics users;
  private DashboardContentMetrics content;
  private DashboardEngagementMetrics engagement;
  private DashboardModerationQueueMetrics moderationQueue;
  private DashboardMoneyMetrics money;
}
