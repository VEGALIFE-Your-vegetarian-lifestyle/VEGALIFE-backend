package com.vegalife.service.admin;

/**
 * Cache names owned by the admin dashboard. The names live beside the service that reads them
 * ({@link AdminDashboardService}) instead of on {@code CacheConfig}, so the service never has to
 * import a configuration class (ADR-018).
 */
public final class AdminDashboardCacheNames {

  /**
   * Backs the lifetime summary. Short TTL because its alerts-adjacent values — pending payments,
   * queue age — are what an admin acts on.
   */
  public static final String ADMIN_DASHBOARD_SUMMARY_CACHE = "adminDashboardSummary";

  /**
   * Backs the chart series. Longer TTL because bucketed aggregates over a closed window barely
   * change, and this is where the {@code date_trunc} cost lives.
   */
  public static final String ADMIN_DASHBOARD_CHARTS_CACHE = "adminDashboardCharts";

  private AdminDashboardCacheNames() {}
}
