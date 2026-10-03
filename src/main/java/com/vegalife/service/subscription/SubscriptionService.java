package com.vegalife.service.subscription;

import com.vegalife.dto.mapper.subscription.SubscriptionMapper;
import com.vegalife.dto.response.subscription.PaymentResponse;
import com.vegalife.dto.response.subscription.PlanSummaryResponse;
import com.vegalife.dto.response.subscription.SubscriptionMeResponse;
import com.vegalife.dto.response.subscription.SubscriptionUsageResponse;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
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
    Optional<AiSubscription> existing = subscriptionRepository.findByUserId(userId);

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
    PaymentResponse latestPayment = resolveLatestPayment(userId, plan);

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
        paymentLedgerRepository.findFirstByUserIdAndStatusOrderByPaidAtDesc(
            userId, PaymentLedger.Status.succeeded);
    if (latest.isEmpty()) {
      return null;
    }

    PaymentLedger ledger = latest.get();
    String planCode;
    if (ledger.getPlanId().equals(plan.getId())) {
      planCode = plan.getCode();
    } else {
      planCode = planRepository.findById(ledger.getPlanId()).map(AiPlan::getCode).orElse(null);
    }
    return subscriptionMapper.toPaymentResponse(ledger, planCode);
  }
}
