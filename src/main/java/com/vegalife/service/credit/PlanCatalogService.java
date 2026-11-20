package com.vegalife.service.credit;

import com.vegalife.dto.mapper.subscription.SubscriptionMapper;
import com.vegalife.dto.response.subscription.AvailablePlanResponse;
import com.vegalife.repository.subscription.AiPlanRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public plan catalogue: active plans only, in display order (issue #181).
 *
 * <p>Every plan is listed whether or not the caller already holds it. Re-acquiring a paid plan is
 * allowed and stacks, so a "already owned" marker would wrongly imply the opposite; and the client
 * can tell a claim from a purchase from {@code price.amount == 0}.
 */
@Service
@RequiredArgsConstructor
public class PlanCatalogService {

  private final AiPlanRepository planRepository;
  private final SubscriptionMapper subscriptionMapper;

  @Transactional(readOnly = true)
  public List<AvailablePlanResponse> getAvailablePlans() {
    return planRepository.findByActiveTrueOrderBySortOrderAsc().stream()
        .map(subscriptionMapper::toAvailablePlan)
        .toList();
  }
}
