package com.vegalife.service.admin;

/**
 * Cache names owned by the admin dashboard. The name lives beside the service that reads it ({@link
 * AdminDashboardService}) instead of on {@code CacheConfig}, so the service never has to import a
 * configuration class (ADR-018).
 */
public final class AdminDashboardCacheNames {

  /** Backs the admin dashboard's cached aggregate metrics (issue #12). */
  public static final String ADMIN_DASHBOARD_CACHE = "adminDashboard";

  private AdminDashboardCacheNames() {}
}
