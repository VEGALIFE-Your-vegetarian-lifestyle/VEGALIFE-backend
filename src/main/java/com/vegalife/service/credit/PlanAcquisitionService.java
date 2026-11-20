package com.vegalife.service.credit;

import com.vegalife.dto.response.subscription.AcquirePlanResponse;
import com.vegalife.enums.subscription.AcquireAction;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.service.payment.PaymentCheckoutService;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one entry point for acquiring a plan (issue #181, ADR-021).
 *
 * <p>The price alone decides the mode: {@code 0} is a claim, anything greater opens a checkout. The
 * two branches are disjoint by construction, so a plan can never be both claimable and purchasable
 * and no "already claimed but also for sale" fall-through exists.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanAcquisitionService {

  private final AiPlanRepository planRepository;
  private final PlanClaimService planClaimService;
  private final PaymentCheckoutService paymentCheckoutService;
  private final RequestAllowanceService requestAllowanceService;

  @Transactional
  public AcquirePlanResponse acquire(UUID userId, String planCode, String ipAddress) {
    String code = planCode.trim().toUpperCase(Locale.ROOT);
    AiPlan plan =
        planRepository
            .findByCode(code)
            .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
    if (!plan.isActive()) {
      throw new ValidationException("Plan is not available");
    }

    if (plan.getPriceAmount() == 0) {
      planClaimService.claimOrThrow(userId, plan);
      log.info("Claimed free plan {} for user {}", plan.getCode(), userId);
      return AcquirePlanResponse.builder()
          .planCode(plan.getCode())
          .action(AcquireAction.CLAIMED)
          .remainingUsage(requestAllowanceService.getRemainingUsage(userId))
          .build();
    }

    PaymentCheckoutService.CheckoutSession session =
        paymentCheckoutService.create(userId, plan, ipAddress);
    return AcquirePlanResponse.builder()
        .planCode(plan.getCode())
        .action(AcquireAction.CHECKOUT_CREATED)
        .remainingUsage(requestAllowanceService.getRemainingUsage(userId))
        .paymentId(session.paymentId())
        .txnRef(session.txnRef())
        .paymentUrl(session.paymentUrl())
        .amount(session.amount())
        .currency(session.currency())
        .status(session.status())
        .build();
  }
}
