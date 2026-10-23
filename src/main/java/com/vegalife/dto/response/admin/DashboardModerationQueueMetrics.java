package com.vegalife.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardModerationQueueMetrics {

  private long pendingPosts;
  private long pendingRecipes;
  private long reportedComments;
}
