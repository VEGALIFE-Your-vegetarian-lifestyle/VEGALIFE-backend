package com.vegalife.unit.service.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.admin.AdminPlanMapper;
import com.vegalife.dto.request.admin.AdminPlanCreateRequest;
import com.vegalife.dto.request.admin.AdminPlanUpdateRequest;
import com.vegalife.dto.response.admin.AdminPlanResponse;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.service.subscription.AdminPlanService;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminPlanServiceTest {

  @Mock private AiPlanRepository aiPlanRepository;

  @Mock private AdminPlanMapper adminPlanMapper;

  @InjectMocks private AdminPlanService adminPlanService;

  @Test
  void createPlanPersistsUppercasedCodeAndCurrency() {
    AdminPlanCreateRequest request =
        AdminPlanCreateRequest.builder()
            .code("pro")
            .name("Pro")
            .monthlyRequestLimit(100)
            .priceAmount(50000L)
            .priceCurrency("vnd")
            .active(true)
            .sortOrder(1)
            .build();
    when(aiPlanRepository.findByCode("PRO")).thenReturn(Optional.empty());
    AiPlan saved = AiPlan.builder().id(UUID.randomUUID()).code("PRO").build();
    when(aiPlanRepository.saveAndFlush(any(AiPlan.class))).thenReturn(saved);
    when(adminPlanMapper.toResponse(saved)).thenReturn(AdminPlanResponse.builder().build());

    adminPlanService.createPlan(request);

    ArgumentCaptor<AiPlan> captor = ArgumentCaptor.forClass(AiPlan.class);
    verify(aiPlanRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getCode()).isEqualTo("PRO");
    assertThat(captor.getValue().getPriceCurrency()).isEqualTo("VND");
  }

  @Test
  void createPlanRejectsDuplicateCode() {
    AdminPlanCreateRequest request =
        AdminPlanCreateRequest.builder()
            .code("FREE")
            .name("Free")
            .monthlyRequestLimit(10)
            .priceAmount(0L)
            .priceCurrency("VND")
            .active(true)
            .sortOrder(0)
            .build();
    when(aiPlanRepository.findByCode("FREE")).thenReturn(Optional.of(AiPlan.builder().build()));

    assertThatThrownBy(() -> adminPlanService.createPlan(request))
        .isInstanceOf(DuplicateResourceException.class);

    verify(aiPlanRepository, never()).saveAndFlush(any());
  }

  @Test
  void updatePlanAppliesOnlySuppliedFields() {
    UUID planId = UUID.randomUUID();
    AiPlan existing = AiPlan.builder().id(planId).code("PRO").name("Pro").sortOrder(1).build();
    when(aiPlanRepository.findById(planId)).thenReturn(Optional.of(existing));
    when(aiPlanRepository.saveAndFlush(any(AiPlan.class))).thenAnswer(inv -> inv.getArgument(0));
    when(adminPlanMapper.toResponse(any(AiPlan.class)))
        .thenReturn(AdminPlanResponse.builder().build());

    adminPlanService.updatePlan(planId, AdminPlanUpdateRequest.builder().sortOrder(5).build());

    ArgumentCaptor<AiPlan> captor = ArgumentCaptor.forClass(AiPlan.class);
    verify(aiPlanRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("Pro");
    assertThat(captor.getValue().getSortOrder()).isEqualTo(5);
  }

  @Test
  void updatePlanThrowsNotFoundWhenMissing() {
    UUID planId = UUID.randomUUID();
    when(aiPlanRepository.findById(planId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                adminPlanService.updatePlan(
                    planId, AdminPlanUpdateRequest.builder().sortOrder(1).build()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void updatePlanRejectsDeactivatingFreePlan() {
    UUID planId = UUID.randomUUID();
    AiPlan free = AiPlan.builder().id(planId).code("FREE").active(true).build();
    when(aiPlanRepository.findById(planId)).thenReturn(Optional.of(free));

    assertThatThrownBy(
            () ->
                adminPlanService.updatePlan(
                    planId, AdminPlanUpdateRequest.builder().active(false).build()))
        .isInstanceOf(DuplicateResourceException.class);

    verify(aiPlanRepository, never()).saveAndFlush(any());
  }

  @Test
  void deactivatePlanSetsActiveFalse() {
    UUID planId = UUID.randomUUID();
    AiPlan plan = AiPlan.builder().id(planId).code("PRO").active(true).build();
    when(aiPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
    when(aiPlanRepository.saveAndFlush(any(AiPlan.class))).thenAnswer(inv -> inv.getArgument(0));

    adminPlanService.deactivatePlan(planId);

    ArgumentCaptor<AiPlan> captor = ArgumentCaptor.forClass(AiPlan.class);
    verify(aiPlanRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().isActive()).isFalse();
  }

  @Test
  void deactivatePlanRejectsFreePlan() {
    UUID planId = UUID.randomUUID();
    AiPlan free = AiPlan.builder().id(planId).code("FREE").active(true).build();
    when(aiPlanRepository.findById(planId)).thenReturn(Optional.of(free));

    assertThatThrownBy(() -> adminPlanService.deactivatePlan(planId))
        .isInstanceOf(DuplicateResourceException.class);

    verify(aiPlanRepository, never()).saveAndFlush(any());
  }

  @Test
  void deactivatePlanThrowsNotFoundWhenMissing() {
    UUID planId = UUID.randomUUID();
    when(aiPlanRepository.findById(planId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminPlanService.deactivatePlan(planId))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
