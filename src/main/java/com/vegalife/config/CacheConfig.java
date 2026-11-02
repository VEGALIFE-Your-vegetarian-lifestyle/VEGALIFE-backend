package com.vegalife.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.vegalife.service.admin.AdminDashboardCacheNames;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Backs the admin dashboard's cached aggregate metrics (issue #12). */
@Configuration
@EnableCaching
public class CacheConfig {

  private static final int TTL_MINUTES = 5;

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager(AdminDashboardCacheNames.ADMIN_DASHBOARD_CACHE);
    cacheManager.setCaffeine(
        Caffeine.newBuilder().expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES).maximumSize(100));
    return cacheManager;
  }
}
