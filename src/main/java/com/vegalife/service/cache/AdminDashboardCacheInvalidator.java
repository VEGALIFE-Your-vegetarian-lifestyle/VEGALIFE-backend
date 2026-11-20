package com.vegalife.service.cache;

import com.vegalife.service.admin.AdminDashboardCacheNames;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Clears the admin dashboard's cached aggregates when a write makes them untrue.
 *
 * <p>Without this the dashboard is correct only as of the moment the entry was built: the summary
 * lives 5 minutes and the charts 15, so an admin who approves a post can load the dashboard
 * straight afterwards and still be shown the queue they just cleared. TTL alone cannot express
 * "this number changed"; it can only express "this number is at most N minutes old".
 *
 * <p>Eviction is deferred to commit on purpose. A write evicts while its transaction is still open,
 * so a dashboard read that races the write can miss the cache, query the database, observe the
 * pre-commit state, and re-populate the entry with stale data that then survives a full TTL. (The
 * dashboard reads are {@code sync = true}, which additionally stops such a load from writing back
 * after this eviction ran.) Evicting after commit removes the window instead of narrowing it.
 *
 * <p>Owned by no domain: it is imported by every service with a write that moves a dashboard
 * number, so it sits in its own leaf package rather than under {@code service/admin}. Putting it
 * there would introduce a {@code service/recipe ↔ service/admin} cycle — {@code AdminRecipeService}
 * already imports {@code service/recipe} — which ADR-018 rules out.
 *
 * <p>Call sites are enumerated in {@code docs/feats/admin-dashboard-cache-invalidation.md}.
 */
@Component
public class AdminDashboardCacheInvalidator {

  private final CacheManager cacheManager;

  public AdminDashboardCacheInvalidator(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  /**
   * Clears the lifetime summary only: a metric an admin acts on moved, but no money or series did.
   */
  public void summary() {
    evict(Set.of(AdminDashboardCacheNames.ADMIN_DASHBOARD_SUMMARY_CACHE));
  }

  /** Clears the bucketed chart series only: revenue, purchases, or plan visibility changed. */
  public void charts() {
    evict(Set.of(AdminDashboardCacheNames.ADMIN_DASHBOARD_CHARTS_CACHE));
  }

  /** Clears both. Used when a single write moves a summary metric and a chart series together. */
  public void all() {
    evict(
        new LinkedHashSet<>(
            Set.of(
                AdminDashboardCacheNames.ADMIN_DASHBOARD_SUMMARY_CACHE,
                AdminDashboardCacheNames.ADMIN_DASHBOARD_CHARTS_CACHE)));
  }

  /**
   * Runs the clear after the surrounding transaction commits, or immediately when there is no
   * transaction to wait for.
   *
   * <p>A rollback must not evict: the write never happened, so the cached value is still correct,
   * and paying for a rebuild would be pure waste.
   */
  private void evict(Set<String> cacheNames) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      clear(cacheNames);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            clear(cacheNames);
          }
        });
  }

  private void clear(Set<String> cacheNames) {
    for (String cacheName : cacheNames) {
      Cache cache = cacheManager.getCache(cacheName);
      if (cache != null) {
        // Not log-and-continue: a swallowed failure here means the dashboard silently keeps serving
        // a number the admin already acted on, and nothing would ever report it.
        cache.clear();
      }
    }
  }
}
