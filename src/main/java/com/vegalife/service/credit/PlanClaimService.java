package com.vegalife.service.credit;

import com.vegalife.model.subscription.AiPlan;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.shared.exception.DuplicateResourceException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims a free plan exactly once per member (issue #181, ADR-021).
 *
 * <p>There is no read-before-write check: two concurrent claims would both pass one, and the loser
 * would fail later while the winner's grant stood. The partial unique index {@code
 * uq_ai_subscription_one_claim} is the actual guarantee — the insert either creates the row or
 * conflicts, and a conflict can only mean "already claimed".
 */
@Service
@RequiredArgsConstructor
public class PlanClaimService {

  private final AiSubscriptionRepository subscriptionRepository;

  /**
   * Grants the plan to the member, or refuses when it is already claimed.
   *
   * @throws DuplicateResourceException when the member already claimed this plan
   */
  @Transactional
  public void claimOrThrow(UUID userId, AiPlan plan) {
    if (subscriptionRepository.insertClaim(userId, plan.getId()) == 0) {
      throw new DuplicateResourceException("Plan already claimed");
    }
  }
}
