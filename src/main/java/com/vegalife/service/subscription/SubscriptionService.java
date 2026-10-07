package com.vegalife.service.subscription;

import com.vegalife.dto.mapper.subscription.SubscriptionMapper;
import com.vegalife.dto.response.subscription.AvailablePlanResponse;
import com.vegalife.dto.response.subscription.PaymentResponse;
import com.vegalife.dto.response.subscription.PlanSummaryResponse;
import com.vegalife.dto.response.subscription.SubscriptionCancelResponse;
import com.vegalife.dto.response.subscription.SubscriptionMeResponse;
import com.vegalife.dto.response.subscription.SubscriptionUsageResponse;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.shared.exception.DuplicateResourceException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

  private static final String FREE_PLAN_CODE = "FREE";

  private final AiSubscriptionRepository subscriptionRepository;
  private final AiPlanRepository planRepository;
  private final AiUsageRepository usageRepository;
  private final PaymentLedgerRepository paymentLedgerRepository;
  private final SubscriptionMapper subscriptionMapper;

  @Transactional(readOnly = true)
  public SubscriptionMeResponse getMySubscription(UUID userId) {
    Optional<AiSubscription> existing = subscriptionRepository.findInEffect(userId);

    AiPlan plan;
    String status;
    Instant renewalDate;
    if (existing.isPresent()) {
      AiSubscription subscription = existing.get();
      plan =
          planRepository
              .findById(subscription.getPlanId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Plan of subscription not found: " + subscription.getPlanId()));
      status = subscription.getStatus().name();
      renewalDate = subscription.getRenewalDate();
    } else {
      // No subscription row: synthesize the FREE default in memory (BR-SUBS-003),
      // never 404 and never write a row.
      plan =
          planRepository
              .findByCode(FREE_PLAN_CODE)
              .orElseThrow(() -> new IllegalStateException("Seeded FREE plan not found"));
      status = AiSubscription.Status.active.name();
      renewalDate = null;
    }

    YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
    Instant windowStart = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant windowEnd =
        currentMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    long used = usageRepository.sumRequestCountInWindow(userId, windowStart, windowEnd);
    SubscriptionUsageResponse usage =
        subscriptionMapper.toUsageResponse(used, plan, windowStart, windowEnd);
    PlanSummaryResponse currentPlan = subscriptionMapper.toPlanSummary(plan);
    // latestPayment only reports a charge on the plan the member is on now:
    // no subscription row means the FREE default and therefore no payment (FR-003),
    // and a payment for some other plan is out of scope for this response (FR-005).
    PaymentResponse latestPayment =
        existing.isPresent() ? resolveLatestPayment(userId, plan) : null;

    return SubscriptionMeResponse.builder()
        .tier(plan.getCode())
        .status(status)
        .renewalDate(renewalDate)
        .usage(usage)
        .currentPlan(currentPlan)
        .latestPayment(latestPayment)
        .build();
  }

  private PaymentResponse resolveLatestPayment(UUID userId, AiPlan plan) {
    Optional<PaymentLedger> latest =
        paymentLedgerRepository.findFirstByUserIdAndPlanIdAndStatusOrderByPaidAtDesc(
            userId, plan.getId(), PaymentLedger.Status.succeeded);
    if (latest.isEmpty()) {
      return null;
    }
    return subscriptionMapper.toPaymentResponse(latest.get(), plan.getCode());
  }

  @Transactional(readOnly = true)
  public List<AvailablePlanResponse> getAvailablePlans() {
    return planRepository.findByActiveTrueOrderBySortOrderAsc().stream()
        .map(subscriptionMapper::toAvailablePlan)
        .toList();
  }

  /**
   * Answers FR-003: cancels the member's row in effect and every scheduled successor in one
   * transaction. Throws 409 when nothing is in effect (FR-004) — a pure guard that writes nothing.
   */
  @Transactional
  public SubscriptionCancelResponse cancelMySubscription(UUID userId) {
    Optional<AiSubscription> inEffect = subscriptionRepository.findInEffectForUpdate(userId);
    if (inEffect.isEmpty()) {
      throw new DuplicateResourceException("Subscription already cancelled");
    }

    Instant cancelledAt = Instant.now();
    AiSubscription subscription = inEffect.get();
    subscription.setStatus(AiSubscription.Status.cancelled);
    subscription.setCancelledAt(cancelledAt);
    subscriptionRepository.save(subscription);
    int cascaded =
        subscriptionRepository.cancelScheduledForUser(
            userId, cancelledAt, AiSubscription.Status.cancelled, AiSubscription.Status.scheduled);
    log.info(
        "Cancelled subscription {} for user {} (cancelledAt={}, scheduledCascaded={})",
        subscription.getId(),
        userId,
        cancelledAt,
        cascaded);

    return SubscriptionCancelResponse.builder()
        .status(AiSubscription.Status.cancelled.name())
        .cancelledAt(cancelledAt)
        .build();
  }

  /**
   * Answers FR-007: whether the member may currently buy the given plan. Re-runs the purchase gate
   * (FR-006) with a read-only query — no row is created, updated, or write-locked — and throws 409
   * on a denial.
   */
  @Transactional(readOnly = true)
  public void checkPurchaseEligibility(UUID userId, UUID planId) {
    GateOutcome outcome = evaluateGate(userId, planId, subscriptionRepository.findInEffect(userId));
    if (outcome.denyMessage != null) {
      throw new DuplicateResourceException(outcome.denyMessage);
    }
  }

  /**
   * Fulfils a successful payment: re-runs the purchase gate (FR-006) under a pessimistic write lock
   * on the member's row in effect and inserts exactly one new row (FR-008): an active row starting
   * at paid time when nothing is in effect, or a scheduled successor chained via {@code
   * extended_from_id} whose window starts at the current {@code renewal_date} when the same plan is
   * being extended. Any other configuration writes nothing and logs WARN — the payment stays
   * succeeded (BR-PAY-001). One calendar month, UTC. The only writer of {@code renewal_date}
   * besides the expiry sweep.
   */
  @Transactional
  public Optional<AiSubscription> activatePlan(UUID userId, UUID planId, Instant paidAt) {
    Optional<AiSubscription> inEffect = subscriptionRepository.findInEffectForUpdate(userId);
    GateOutcome outcome = evaluateGate(userId, planId, inEffect);

    return switch (outcome) {
      case NEW -> {
        AiSubscription saved =
            subscriptionRepository.save(
                AiSubscription.builder()
                    .userId(userId)
                    .planId(planId)
                    .status(AiSubscription.Status.active)
                    .startedAt(paidAt)
                    .renewalDate(paidAt.atZone(ZoneOffset.UTC).plusMonths(1).toInstant())
                    .build());
        log.info(
            "Activated plan {} for user {} (paidAt={}, renewalDate={})",
            planId,
            userId,
            paidAt,
            saved.getRenewalDate());
        yield Optional.of(saved);
      }
      case EXTENSION -> {
        AiSubscription current = inEffect.get();
        Instant base = current.getRenewalDate() != null ? current.getRenewalDate() : paidAt;
        AiSubscription saved =
            subscriptionRepository.save(
                AiSubscription.builder()
                    .userId(userId)
                    .planId(planId)
                    .status(AiSubscription.Status.scheduled)
                    .startedAt(base)
                    .renewalDate(base.atZone(ZoneOffset.UTC).plusMonths(1).toInstant())
                    .extendedFromId(current.getId())
                    .build());
        log.info(
            "Scheduled renewal of plan {} for user {} (startedAt={}, renewalDate={})",
            planId,
            userId,
            saved.getStartedAt(),
            saved.getRenewalDate());
        yield Optional.of(saved);
      }
      case DENY_DIFFERENT_PLAN, DENY_ALREADY_SCHEDULED -> {
        log.warn(
            "Purchase of plan {} for user {} denied by subscription gate: {}",
            planId,
            userId,
            outcome.denyMessage);
        yield Optional.empty();
      }
    };
  }

  private GateOutcome evaluateGate(UUID userId, UUID planId, Optional<AiSubscription> inEffect) {
    if (inEffect.isEmpty()) {
      return GateOutcome.NEW;
    }
    if (!planId.equals(inEffect.get().getPlanId())) {
      return GateOutcome.DENY_DIFFERENT_PLAN;
    }
    boolean scheduledExists =
        subscriptionRepository.existsByUserIdAndPlanIdAndStatus(
            userId, planId, AiSubscription.Status.scheduled);
    return scheduledExists ? GateOutcome.DENY_ALREADY_SCHEDULED : GateOutcome.EXTENSION;
  }

  private enum GateOutcome {
    NEW(null),
    EXTENSION(null),
    DENY_DIFFERENT_PLAN("Cancel your current subscription before purchasing a different plan"),
    DENY_ALREADY_SCHEDULED("A renewal is already scheduled for this plan");

    private final String denyMessage;

    GateOutcome(String denyMessage) {
      this.denyMessage = denyMessage;
    }
  }
}
