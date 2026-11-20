package com.vegalife.service.credit;

import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.shared.exception.AiQuotaExceededException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single source of truth for how many AI requests a member has left (issue #181, ADR-021).
 *
 * <pre>
 * allowance      = SUM(ai_plan.request_quota) over the member's granted acquisitions
 * consumed       = ai_usage.request_count
 * remainingUsage = max(allowance - consumed, 0)
 * </pre>
 *
 * <p>Only {@code remainingUsage} is ever exposed. The allowance and the consumption are internal
 * numbers, and the allowance is not stored anywhere: it is recomputed from the acquisitions on
 * every read, so an admin's quota edit applies retroactively to everyone holding the plan.
 *
 * <p>The allowance query deliberately does not filter {@code ai_plan.active}. Deactivating a plan
 * stops new acquisition; it must never revoke allowance already granted.
 */
@Service
@RequiredArgsConstructor
public class RequestAllowanceService {

  private static final String EXHAUSTED_MESSAGE =
      "No AI requests remaining. Claim a free plan or purchase one to continue.";

  private final AiSubscriptionRepository subscriptionRepository;
  private final AiUsageRepository usageRepository;

  /** The member's remaining requests, never negative. */
  @Transactional(readOnly = true)
  public long getRemainingUsage(UUID userId) {
    long allowance = subscriptionRepository.sumGrantedQuota(userId);
    long consumed = usageRepository.sumRequestCount(userId);
    return Math.max(allowance - consumed, 0);
  }

  /**
   * Returns the remaining requests when at least one is left, otherwise refuses the send.
   *
   * @throws AiQuotaExceededException when the member has none left
   */
  @Transactional(readOnly = true)
  public long requireAllowance(UUID userId) {
    long remaining = getRemainingUsage(userId);
    if (remaining <= 0) {
      throw new AiQuotaExceededException(EXHAUSTED_MESSAGE);
    }
    return remaining;
  }

  /**
   * Counts one successful request (BR-AI-002). Joins the caller's transaction when one is active so
   * the increment commits with the message persistence.
   */
  @Transactional
  public void record(UUID userId) {
    usageRepository.incrementRequestCount(userId);
  }
}
