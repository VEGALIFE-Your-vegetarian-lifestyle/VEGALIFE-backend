package com.vegalife.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What needs an admin's attention right now. Every value is a current snapshot: a queue is not a
 * windowed metric, so none of these follow a time range.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardModerationQueueMetrics {

  /** Non-deleted posts with {@code status = pending} — the same predicate as the review queue. */
  private long pendingPosts;

  private long pendingRecipes;

  private long reportedComments;

  /**
   * Age of the single oldest item across all three queues, in whole hours (0 when nothing is
   * waiting). More actionable than the counts: three items waiting six days is worse than thirty
   * waiting an hour.
   */
  private long oldestPendingAgeHours;

  /**
   * Posts whose content filter never produced a verdict ({@code flag = FILTER_SKIPPED}) — a silent
   * quality hole, since nothing else surfaces it.
   */
  private long filterSkippedPosts;
}
