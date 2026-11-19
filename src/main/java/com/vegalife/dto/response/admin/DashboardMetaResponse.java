package com.vegalife.dto.response.admin;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provenance and units for a dashboard payload. The response is cached, so {@code generatedAt} —
 * not the window boundaries — is the only freshness claim a caller may render.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetaResponse {

  /** When this payload was produced. The response is cached, so this is the freshness signal. */
  private Instant generatedAt;

  /**
   * Currency of every amount in {@link DashboardMoneyMetrics}. Always {@code VND}: the platform
   * settles in VND only, and every amount is in whole dong even though the gateway notifies
   * hundredths.
   */
  private String currency;
}
