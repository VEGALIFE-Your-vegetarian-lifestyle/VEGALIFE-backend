package com.vegalife.repository.subscription;

import com.vegalife.model.subscription.AiUsage;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
