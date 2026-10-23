package com.vegalife.service.subscription;

import com.vegalife.dto.mapper.admin.AdminPlanMapper;
import com.vegalife.dto.request.admin.AdminPlanCreateRequest;
import com.vegalife.dto.request.admin.AdminPlanUpdateRequest;
import com.vegalife.dto.response.admin.AdminPlanResponse;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin CRUD for {@code ai_plan} rows (issue #119). {@code code} is immutable once created — it is
 * referenced by {@code ai_subscription.plan_id} and {@code payment_ledger.plan_id} — and the FREE
 * fallback plan can never be deactivated (BR-SUBS-003 synthesizes FREE from that row).
 */
@Service
@RequiredArgsConstructor
public class AdminPlanService {

  private static final String FREE_PLAN_CODE = "FREE";

  private final AiPlanRepository aiPlanRepository;
  private final AdminPlanMapper adminPlanMapper;

  @Transactional(readOnly = true)
  public List<AdminPlanResponse> listPlans() {
    return aiPlanRepository.findAll().stream()
        .sorted(Comparator.comparingInt(AiPlan::getSortOrder))
        .map(adminPlanMapper::toResponse)
        .toList();
  }

  @Transactional
  public AdminPlanResponse createPlan(AdminPlanCreateRequest request) {
    String code = request.getCode().trim().toUpperCase();
    if (aiPlanRepository.findByCode(code).isPresent()) {
      throw new DuplicateResourceException("Plan code already exists");
    }

    AiPlan plan =
        AiPlan.builder()
            .code(code)
            .name(request.getName().trim())
            .monthlyRequestLimit(request.getMonthlyRequestLimit())
            .priceAmount(request.getPriceAmount())
            .priceCurrency(request.getPriceCurrency().trim().toUpperCase())
            .active(request.getActive())
            .sortOrder(request.getSortOrder())
            .build();

    AiPlan saved = aiPlanRepository.saveAndFlush(plan);
    return adminPlanMapper.toResponse(saved);
  }

  @Transactional
  public AdminPlanResponse updatePlan(UUID planId, AdminPlanUpdateRequest request) {
    AiPlan plan = findPlan(planId);

    if (request.getName() != null) {
      plan.setName(request.getName().trim());
    }
    if (request.getMonthlyRequestLimit() != null) {
      plan.setMonthlyRequestLimit(request.getMonthlyRequestLimit());
    }
    if (request.getPriceAmount() != null) {
      plan.setPriceAmount(request.getPriceAmount());
    }
    if (request.getPriceCurrency() != null) {
      plan.setPriceCurrency(request.getPriceCurrency().trim().toUpperCase());
    }
    if (request.getActive() != null) {
      if (FREE_PLAN_CODE.equals(plan.getCode()) && !request.getActive()) {
        throw new DuplicateResourceException("FREE plan must always remain active");
      }
      plan.setActive(request.getActive());
    }
    if (request.getSortOrder() != null) {
      plan.setSortOrder(request.getSortOrder());
    }

    AiPlan saved = aiPlanRepository.saveAndFlush(plan);
    return adminPlanMapper.toResponse(saved);
  }

  @Transactional
  public void deactivatePlan(UUID planId) {
    AiPlan plan = findPlan(planId);
    if (FREE_PLAN_CODE.equals(plan.getCode())) {
      throw new DuplicateResourceException("FREE plan must always remain active");
    }
    plan.setActive(false);
    aiPlanRepository.saveAndFlush(plan);
  }

  private AiPlan findPlan(UUID planId) {
    return aiPlanRepository
        .findById(planId)
        .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
  }
}
