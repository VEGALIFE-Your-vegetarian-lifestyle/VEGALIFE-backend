package com.vegalife.dto.response.admin;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provenance for a dashboard payload. The response is cached, so {@code generatedAt} — not the
 * window boundaries — is the only freshness claim a caller may render.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetaResponse {

  private Instant generatedAt;

  /**
   * Currency of {@link DashboardMoneyMetrics#getRevenueTotal()}, or {@code null} when the ledger
   * holds no succeeded payment or holds more than one currency — a total summed across currencies
   * would be meaningless, so the mixed case is signalled rather than hidden.
   */
  private String currency;

  /**
   * Decimal exponent of {@link #currency} (0 for VND). {@code payment_ledger.amount} is stored in
   * whole currency units while the gateway notifies hundredths, so a client that assumes 2 would
   * misplace the decimal.
   */
  private int currencyExponent;
}
