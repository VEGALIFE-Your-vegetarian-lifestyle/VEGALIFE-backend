package com.vegalife.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vegalife.service.admin.AdminDashboardCacheNames;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Backs the admin dashboard's cached aggregates.
 *
 * <p>The two dashboard endpoints are cached separately and with different TTLs on purpose: the
 * lifetime summary is cheap and must stay close to live, while the chart series run bucketed {@code
 * date_trunc} queries over a window and change slowly. Sharing one entry would force the
 * alert-adjacent summary to age at the charts' pace.
 */
@Configuration
@EnableCaching
public class CacheConfig {

  private static final int SUMMARY_TTL_MINUTES = 5;
  private static final int CHARTS_TTL_MINUTES = 15;
  private static final int MAX_ENTRIES = 100;

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager();
    cacheManager.registerCustomCache(
        AdminDashboardCacheNames.ADMIN_DASHBOARD_SUMMARY_CACHE, cache(SUMMARY_TTL_MINUTES));
    cacheManager.registerCustomCache(
        AdminDashboardCacheNames.ADMIN_DASHBOARD_CHARTS_CACHE, cache(CHARTS_TTL_MINUTES));
    return cacheManager;
  }

  private static Cache<Object, Object> cache(int ttlMinutes) {
    return Caffeine.newBuilder()
        .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
        .maximumSize(MAX_ENTRIES)
        .build();
  }
}
