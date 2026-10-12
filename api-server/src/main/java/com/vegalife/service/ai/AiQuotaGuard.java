package com.vegalife.service.ai;

import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.shared.exception.AiQuotaExceededException;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the monthly AI allowance (BR-AI-001): resolves the member's plan limit and current
 * UTC-month usage before the provider call, and atomically counts the request only after a
 * successful reply (BR-AI-002). Window semantics belong to BR-SUBS-001 (SubscriptionService) — this
 * guard reuses them rather than defining its own.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiQuotaGuard {

  private static final String FREE_PLAN_CODE = "FREE";

  private final AiSubscriptionRepository subscriptionRepository;
  private final AiPlanRepository planRepository;
  private final AiUsageRepository usageRepository;

  /**
   * The current UTC-month allowance as seen right before a send: window, plan limit, usage so far.
   */
  public record QuotaWindow(Instant start, Instant end, int limit, long used) {}

  /**
   * Resolves the allowance for the current UTC month and refuses at/over the limit with a
   * Retry-After hint. Reads only — writes nothing. The returned snapshot must be handed back to
   * {@link #record} so the increment lands in the same window that was checked.
   *
   * @throws AiQuotaExceededException when the member has spent the monthly allowance
   */
  @Transactional(readOnly = true)
  public QuotaWindow requireAllowance(UUID userId) {
    YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
    Instant windowStart = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant windowEnd =
        currentMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    AiPlan plan = resolvePlan(userId);
    long used = usageRepository.sumRequestCountInWindow(userId, windowStart, windowEnd);
    int limit = plan.getMonthlyRequestLimit();
    if (used >= limit) {
      long retryAfterSeconds = Math.max(1, Duration.between(Instant.now(), windowEnd).getSeconds());
      log.warn(
          "AI quota exceeded for user {} (used {}/{}, window {}..{})",
          userId,
          used,
          limit,
          windowStart,
          windowEnd);
      throw new AiQuotaExceededException(
          "Monthly AI request limit reached (%d/%d). Resets at the start of the next UTC month."
              .formatted(used, limit),
          retryAfterSeconds);
    }
    return new QuotaWindow(windowStart, windowEnd, limit, used);
  }

  /**
   * Counts one successful request against the window returned by {@link #requireAllowance}. Joins
   * the caller's transaction when one is active so the increment commits with the message
   * persistence, or opens its own when called standalone.
   */
  @Transactional
  public void record(UUID userId, QuotaWindow window) {
    usageRepository.incrementRequestCount(userId, window.start(), window.end());
    log.debug("Recorded AI usage for user {} ({} in window)", userId, window.used() + 1);
  }

  private AiPlan resolvePlan(UUID userId) {
    Optional<AiSubscription> inEffect = subscriptionRepository.findInEffect(userId);
    if (inEffect.isPresent()) {
      UUID planId = inEffect.get().getPlanId();
      return planRepository
          .findById(planId)
          .orElseThrow(
              () -> new IllegalStateException("Plan of subscription not found: " + planId));
    }
    return planRepository
        .findByCode(FREE_PLAN_CODE)
        .orElseThrow(() -> new IllegalStateException("Seeded FREE plan not found"));
  }
}
