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
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin CRUD for {@code ai_plan} rows (issue #119, revised by issue #181).
 *
 * <p>{@code code} is immutable once created: it is referenced by {@code ai_subscription.plan_id}
 * and is what {@code POST /api/subscriptions/acquire} accepts. Every other field is editable,
 * including {@code requestQuota} — a quota edit is retroactive for everyone holding the plan, in
 * both directions, because the allowance is a live sum with no per-purchase snapshot.
 *
 * <p>No row is special any more. The FREE plan may be deactivated (soft-deleted) like any other:
 * deactivation stops new claims but never revokes allowance already granted. There is no hard
 * delete, here or anywhere else — {@code ai_subscription} references the plan, and with no snapshot
 * a missing plan would make held allowance uncomputable.
 */
@Service
@RequiredArgsConstructor
public class AdminPlanService {

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
    String code = request.getCode().trim().toUpperCase(Locale.ROOT);
    if (aiPlanRepository.findByCode(code).isPresent()) {
      throw new DuplicateResourceException("Plan code already exists");
    }

    AiPlan plan =
        AiPlan.builder()
            .code(code)
            .name(request.getName().trim())
            .description(trimToNull(request.getDescription()))
            .requestQuota(request.getRequestQuota())
            .priceAmount(request.getPriceAmount())
            .priceCurrency(request.getPriceCurrency().trim().toUpperCase(Locale.ROOT))
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
    if (request.getDescription() != null) {
      plan.setDescription(trimToNull(request.getDescription()));
    }
    if (request.getRequestQuota() != null) {
      plan.setRequestQuota(request.getRequestQuota());
    }
    if (request.getPriceAmount() != null) {
      plan.setPriceAmount(request.getPriceAmount());
    }
    if (request.getPriceCurrency() != null) {
      plan.setPriceCurrency(request.getPriceCurrency().trim().toUpperCase(Locale.ROOT));
    }
    if (request.getActive() != null) {
      plan.setActive(request.getActive());
    }
    if (request.getSortOrder() != null) {
      plan.setSortOrder(request.getSortOrder());
    }

    AiPlan saved = aiPlanRepository.saveAndFlush(plan);
    return adminPlanMapper.toResponse(saved);
  }

  /**
   * Soft delete. The row stays so acquisitions can still resolve their plan, and allowance already
   * granted stays computable.
   */
  @Transactional
  public void deactivatePlan(UUID planId) {
    AiPlan plan = findPlan(planId);
    plan.setActive(false);
    aiPlanRepository.saveAndFlush(plan);
  }

  private AiPlan findPlan(UUID planId) {
    return aiPlanRepository
        .findById(planId)
        .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
