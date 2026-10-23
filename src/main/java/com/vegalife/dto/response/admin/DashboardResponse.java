package com.vegalife.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

  private DashboardRangeResponse range;
  private DashboardUserMetrics users;
  private DashboardContentMetrics content;
  private DashboardEngagementMetrics engagement;
  private DashboardModerationQueueMetrics moderationQueue;
}
