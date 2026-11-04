package com.vegalife.repository.subscription;

import com.vegalife.model.subscription.AiUsage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {

  /**
   * The member's lifetime consumption. Reads 0 when no request was ever counted.
   *
   * <p>There is at most one row per member ({@code uq_ai_usage_user}); the sum is expressed as a
   * sum so a missing row and an empty table both answer 0 without a null check.
   */
  @Query("select coalesce(sum(u.requestCount), 0) from AiUsage u where u.userId = :userId")
  long sumRequestCount(@Param("userId") UUID userId);

  /**
   * Counts one successful request (BR-AI-002). Inserts the member's only row or bumps it in place,
   * so concurrent sends can never both read the same stale count. Runs inside the caller's
   * transaction — the caller already knows the reply succeeded.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO ai_usage (user_id, request_count) VALUES (:userId, 1)"
              + " ON CONFLICT (user_id)"
              + " DO UPDATE SET request_count = ai_usage.request_count + 1",
      nativeQuery = true)
  int incrementRequestCount(@Param("userId") UUID userId);
}
