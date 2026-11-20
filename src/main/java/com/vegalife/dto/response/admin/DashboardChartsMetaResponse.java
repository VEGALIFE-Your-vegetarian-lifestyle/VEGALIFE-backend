package com.vegalife.dto.response.admin;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The resolved time window behind a charts payload. Buckets are UTC-aligned and the last bucket may
 * be partial, because the window ends at "now" rather than at a bucket boundary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardChartsMetaResponse {

  /**
   * The requested preset, echoed back after validation: {@code day}, {@code week} or {@code month}.
   */
  private String range;

  /**
   * Bucket size, derived from {@link #range} by the server — never supplied by the caller: {@code
   * hour} for {@code day}, {@code day} for {@code week} and {@code month}. A client that sent its
   * own value could not be kept aligned with the other series.
   */
  private String interval;

  /** Start of the window, inclusive. */
  private Instant from;

  /** End of the window, exclusive. */
  private Instant to;

  /** Always {@code inclusiveStartExclusiveEnd}; stated so clients label the axis correctly. */
  private String boundary;

  /**
   * Currency of every amount in {@link RevenueByPlanResponse}. Always {@code VND}: the platform
   * settles in VND only, and every amount is in whole dong even though the gateway notifies
   * hundredths.
   */
  private String currency;

  /** When this payload was produced. The response is cached, so this is the freshness signal. */
  private Instant generatedAt;
}
