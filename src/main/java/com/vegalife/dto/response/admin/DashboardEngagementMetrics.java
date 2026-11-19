package com.vegalife.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lifetime engagement totals. The up/down split makes sentiment readable, not just volume. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardEngagementMetrics {

  private long totalVotes;
  private long votesUp;
  private long votesDown;

  private long totalSaves;
  private long savedPosts;
  private long savedRecipes;
}
