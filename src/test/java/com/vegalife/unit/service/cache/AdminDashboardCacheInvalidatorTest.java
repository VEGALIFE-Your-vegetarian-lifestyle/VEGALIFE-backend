package com.vegalife.unit.service.cache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.service.admin.AdminDashboardCacheNames;
import com.vegalife.service.cache.AdminDashboardCacheInvalidator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AdminDashboardCacheInvalidatorTest {

  private static final String SUMMARY = AdminDashboardCacheNames.ADMIN_DASHBOARD_SUMMARY_CACHE;
  private static final String CHARTS = AdminDashboardCacheNames.ADMIN_DASHBOARD_CHARTS_CACHE;

  @Mock private CacheManager cacheManager;

  @Mock private Cache summaryCache;

  @Mock private Cache chartsCache;

  private AdminDashboardCacheInvalidator invalidator;

  @BeforeEach
  void setUp() {
    // Lenient: each test exercises one intent, so the stub for the other cache is legitimately
    // unused. The strict alternative would be re-stubbing in every test for no extra safety.
    lenient().when(cacheManager.getCache(SUMMARY)).thenReturn(summaryCache);
    lenient().when(cacheManager.getCache(CHARTS)).thenReturn(chartsCache);
    invalidator = new AdminDashboardCacheInvalidator(cacheManager);
  }

  @AfterEach
  void clearSynchronization() {
    // A leaked synchronization would make the next test see an active "transaction" that never
    // commits, so every eviction in it would look deferred.
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  // ---------------------------------------------------------------------
  // Which cache each intent targets
  // ---------------------------------------------------------------------

  @Test
  void summary_clearsOnlyTheSummaryCache() {
    invalidator.summary();

    verify(summaryCache).clear();
    verifyNoInteractions(chartsCache);
  }

  @Test
  void charts_clearsOnlyTheChartsCache() {
    invalidator.charts();

    verify(chartsCache).clear();
    verifyNoInteractions(summaryCache);
  }

  @Test
  void all_clearsBoth() {
    invalidator.all();

    verify(summaryCache).clear();
    verify(chartsCache).clear();
  }

  // ---------------------------------------------------------------------
  // Deferral to afterCommit
  // ---------------------------------------------------------------------

  @Test
  void outsideATransaction_clearsImmediately() {
    invalidator.all();

    verify(summaryCache).clear();
    verify(chartsCache).clear();
  }

  @Test
  void insideATransaction_doesNotClearUntilItCommits() {
    beginTransaction();

    invalidator.all();

    // Still inside the transaction: clearing now would let a racing read re-populate the entry from
    // pre-commit state, which is the whole reason the clear is deferred.
    verify(summaryCache, never()).clear();
    verify(chartsCache, never()).clear();

    triggerAfterCommit();

    verify(summaryCache).clear();
    verify(chartsCache).clear();
  }

  @Test
  void insideATransactionThatRollsBack_neverClears() {
    beginTransaction();

    invalidator.all();
    triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

    // The write never happened, so the cached value is still true and a rebuild would be waste.
    verify(summaryCache, never()).clear();
    verify(chartsCache, never()).clear();
  }

  @Test
  void insideATransaction_doesNotClearAfterCompletionWithoutCommit() {
    beginTransaction();

    invalidator.summary();
    triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

    // afterCommit is the only hook wired to the clear; a completion callback must not double-clear
    // or clear on paths that skipped a commit.
    verify(summaryCache, never()).clear();
  }

  @Test
  void registeredOncePerCall() {
    beginTransaction();

    invalidator.summary();
    invalidator.summary();

    assertThatCode(this::triggerAfterCommit).doesNotThrowAnyException();
    verify(summaryCache, org.mockito.Mockito.times(2)).clear();
  }

  // ---------------------------------------------------------------------
  // A cache the manager does not know about must not break the write
  // ---------------------------------------------------------------------

  @Test
  void unknownCacheName_isToleratedRatherThanFailingTheWrite() {
    when(cacheManager.getCache(SUMMARY)).thenReturn(null);
    when(cacheManager.getCache(CHARTS)).thenReturn(null);

    assertThatCode(invalidator::all).doesNotThrowAnyException();

    verify(cacheManager).getCache(SUMMARY);
    verify(cacheManager).getCache(CHARTS);
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private void beginTransaction() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
  }

  private void triggerAfterCommit() {
    for (TransactionSynchronization synchronization : synchronizations()) {
      synchronization.afterCommit();
    }
  }

  private void triggerAfterCompletion(int status) {
    for (TransactionSynchronization synchronization : synchronizations()) {
      synchronization.afterCompletion(status);
    }
  }

  private List<TransactionSynchronization> synchronizations() {
    return List.copyOf(TransactionSynchronizationManager.getSynchronizations());
  }
}
