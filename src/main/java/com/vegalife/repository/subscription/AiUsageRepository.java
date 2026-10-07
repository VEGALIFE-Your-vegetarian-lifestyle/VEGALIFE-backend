package com.vegalife.repository.subscription;

import com.vegalife.model.subscription.AiUsage;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {

  /**
   * Sum request counts of all usage rows whose period overlaps the window (BR-SUBS-001): a row
   * counts fully whenever it touches the window, which is correct only while the recorder writes
   * non-overlapping periods.
   */
  @Query(
      "SELECT COALESCE(SUM(u.requestCount), 0) FROM AiUsage u"
          + " WHERE u.userId = :userId AND u.periodStart < :windowEnd"
          + " AND u.periodEnd > :windowStart")
  long sumRequestCountInWindow(
      @Param("userId") UUID userId,
      @Param("windowStart") Instant windowStart,
      @Param("windowEnd") Instant windowEnd);

  /**
   * Counts one successful request against the member's period (BR-AI-002): inserts the period's row
   * or bumps it in place, so concurrent sends can never both read the same stale count. Runs inside
   * the caller's transaction — the caller already knows the reply succeeded.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO ai_usage (user_id, request_count, period_start, period_end)"
              + " VALUES (:userId, 1, :windowStart, :windowEnd)"
              + " ON CONFLICT (user_id, period_start, period_end)"
              + " DO UPDATE SET request_count = ai_usage.request_count + 1",
      nativeQuery = true)
  int incrementRequestCount(
      @Param("userId") UUID userId,
      @Param("windowStart") Instant windowStart,
      @Param("windowEnd") Instant windowEnd);
}
