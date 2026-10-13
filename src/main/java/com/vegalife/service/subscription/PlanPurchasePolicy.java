package com.vegalife.service.subscription;

import com.vegalife.model.subscription.AiPlan;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shared purchasable-plan validation (NFR-MAINT-001): one source of truth for the 404/400 responses
 * a plan must produce before it can be bought or evaluated for purchase eligibility.
 *
 * <p>Unknown plan → 404 "Plan not found"; inactive → 400; price ≤ 0 → 400; non-VND → 400. Both
 * checkout (BR-PAY-*) and the subscription eligibility gate (BR-SUBS-004) delegate here so the
 * messages can never drift.
 */
@Component
@RequiredArgsConstructor
public class PlanPurchasePolicy {

  private static final String CURRENCY_VND = "VND";

  private final AiPlanRepository planRepository;

  /** Resolves a plan by code and enforces purchasability, or throws 404/400. */
  public AiPlan requirePurchasableByCode(String planCode) {
    AiPlan plan =
        planRepository
            .findByCode(planCode)
            .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
    requirePurchasable(plan);
    return plan;
  }

  /** Enforces purchasability of a resolved plan, or throws 400. */
  public void requirePurchasable(AiPlan plan) {
    if (!plan.isActive()) {
      throw new ValidationException("Plan is not active");
    }
    if (plan.getPriceAmount() <= 0) {
      throw new ValidationException("Plan is not purchasable: price must be greater than zero");
    }
    if (!CURRENCY_VND.equals(plan.getPriceCurrency())) {
      throw new ValidationException("Plan is not purchasable: only VND plans can be purchased");
    }
  }
}
