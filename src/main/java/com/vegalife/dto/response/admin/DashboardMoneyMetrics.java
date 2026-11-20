package com.vegalife.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lifetime money metrics. Every amount is in whole dong (VND), matching {@code
 * payment_ledger.amount}; {@link DashboardMetaResponse#getCurrency()} names that currency. The
 * gateway notifies hundredths, but the ledger and this payload are already whole dong.
 *
 * <p>Only {@code status = succeeded} counts as revenue. There is no refund write path in this
 * codebase, so {@code refunded} rows never exist and the total is <em>gross</em> — it is
 * deliberately not described as net.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMoneyMetrics {

  private long revenueTotal;

  private long transactionsTotal;

  private long payingUsersTotal;

  /** {@code revenueTotal / transactionsTotal}. Zero when nothing succeeded. */
  private double averageOrderValue;

  /** {@code revenueTotal / payingUsersTotal}. Zero when nobody has paid. */
  private double arpu;

  /**
   * Acquisitions granted for free ({@code ai_subscription.payment_id is null}). Allowance handed
   * out with no money behind it, which is a cost the revenue figure does not show.
   */
  private long freeGrantsTotal;

  /** {@code payingUsersTotal / users.total}, as a fraction. Zero when there are no accounts. */
  private double payerConversionRate;

  /**
   * Payments still {@code pending} more than 24 hours after checkout. Nothing expires or reconciles
   * them, so this pool only grows: it is money that tried and never arrived.
   */
  private long pendingStaleCount;
}
