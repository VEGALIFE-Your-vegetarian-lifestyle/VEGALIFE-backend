package com.vegalife.dto.response.admin;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Money that did not arrive, one point per bucket. The four values partition the checkout attempts
 * that started in that bucket, which is what makes a stacked bar the right shape: the segments sum
 * to a real quantity.
 *
 * <p>Draw worst-on-top (succeeded, fresh pending, failed, stale pending) so the eye lands on the
 * problem without reading the legend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHealthResponse {

  private List<Point> points;

  private Totals totals;

  /**
   * One bucket's outcomes.
   *
   * <p>{@code failed} is bucketed by {@code updated_at}: the ledger has no {@code failed_at}
   * column, so this is a proxy and is labelled as such rather than presented as exact.
   *
   * <p>{@code pendingStale} counts payments still pending more than 24 hours after checkout.
   * Nothing expires or reconciles them, so that pool only grows.
   */
  public record Point(
      Instant bucketStart, long succeeded, long failed, long pendingFresh, long pendingStale) {}

  /** Window totals, plus the success rate derived from the two decided outcomes. */
  public record Totals(
      long succeeded, long failed, long pendingFresh, long pendingStale, double successRate) {}
}
