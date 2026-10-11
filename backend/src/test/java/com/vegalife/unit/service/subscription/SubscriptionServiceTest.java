package com.vegalife.unit.service.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.subscription.SubscriptionMapper;
import com.vegalife.dto.request.subscription.SubscriptionHistoryRequest;
import com.vegalife.dto.response.subscription.AvailablePlanResponse;
import com.vegalife.dto.response.subscription.PaymentResponse;
import com.vegalife.dto.response.subscription.PlanPriceResponse;
import com.vegalife.dto.response.subscription.PlanSummaryResponse;
import com.vegalife.dto.response.subscription.SubscriptionCancelResponse;
import com.vegalife.dto.response.subscription.SubscriptionHistoryResponse;
import com.vegalife.dto.response.subscription.SubscriptionMeResponse;
import com.vegalife.dto.response.subscription.SubscriptionUsageResponse;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.service.subscription.SubscriptionService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.DuplicateResourceException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

  @Mock private AiSubscriptionRepository subscriptionRepository;
  @Mock private AiPlanRepository planRepository;
  @Mock private AiUsageRepository usageRepository;
  @Mock private PaymentLedgerRepository paymentLedgerRepository;
  @Mock private SubscriptionMapper subscriptionMapper;

  @InjectMocks private SubscriptionService subscriptionService;

  private UUID userId;
  private AiPlan freePlan;
  private AiPlan proPlan;
  private SubscriptionUsageResponse usageResponse;
  private PlanSummaryResponse freePlanSummary;
  private PlanSummaryResponse proPlanSummary;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    freePlan =
        AiPlan.builder()
            .id(UUID.randomUUID())
            .code("FREE")
            .name("Free")
            .monthlyRequestLimit(20)
            .priceAmount(0)
            .priceCurrency("VND")
            .build();
    proPlan =
        AiPlan.builder()
            .id(UUID.randomUUID())
            .code("PRO")
            .name("Pro")
            .monthlyRequestLimit(500)
            .priceAmount(49000)
            .priceCurrency("VND")
            .build();
    usageResponse = SubscriptionUsageResponse.builder().used(0).limit(20).build();
    freePlanSummary =
        PlanSummaryResponse.builder().code("FREE").name("Free").monthlyRequestLimit(20).build();
    proPlanSummary =
        PlanSummaryResponse.builder().code("PRO").name("Pro").monthlyRequestLimit(500).build();
  }

  @Test
  void getMySubscription_whenNoSubscriptionRow_synthesizesFreeDefaultWithoutWriting() {
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.empty());
    when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
    when(usageRepository.sumRequestCountInWindow(
            eq(userId), any(Instant.class), any(Instant.class)))
        .thenReturn(0L);
    when(subscriptionMapper.toUsageResponse(
            eq(0L), eq(freePlan), any(Instant.class), any(Instant.class)))
        .thenReturn(usageResponse);
    when(subscriptionMapper.toPlanSummary(freePlan)).thenReturn(freePlanSummary);

    SubscriptionMeResponse result = subscriptionService.getMySubscription(userId);

    assertThat(result.getTier()).isEqualTo("FREE");
    assertThat(result.getStatus()).isEqualTo("active");
    assertThat(result.getRenewalDate()).isNull();
    assertThat(result.getUsage()).isEqualTo(usageResponse);
    assertThat(result.getCurrentPlan()).isEqualTo(freePlanSummary);
    assertThat(result.getLatestPayment()).isNull();
    verify(subscriptionRepository, never()).save(any(AiSubscription.class));
    verify(planRepository, never()).save(any(AiPlan.class));
    // FR-003: the FREE default is synthesized without touching the payment ledger at all.
    verifyNoInteractions(paymentLedgerRepository);
  }

  @Test
  void getMySubscription_usageWindow_isCurrentUtcMonthBounds() {
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.empty());
    when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
    when(usageRepository.sumRequestCountInWindow(
            eq(userId), any(Instant.class), any(Instant.class)))
        .thenReturn(5L);
    when(subscriptionMapper.toUsageResponse(
            eq(5L), eq(freePlan), any(Instant.class), any(Instant.class)))
        .thenReturn(usageResponse);
    when(subscriptionMapper.toPlanSummary(freePlan)).thenReturn(freePlanSummary);

    subscriptionService.getMySubscription(userId);

    ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(usageRepository)
        .sumRequestCountInWindow(eq(userId), startCaptor.capture(), endCaptor.capture());

    YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
    assertThat(startCaptor.getValue())
        .isEqualTo(currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    assertThat(endCaptor.getValue())
        .isEqualTo(currentMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    assertThat(endCaptor.getValue()).isAfter(startCaptor.getValue());
  }

  @Test
  void getMySubscription_whenSubscriptionExists_returnsPlanStatusRenewalAndPayment() {
    Instant renewalDate = Instant.parse("2026-11-01T00:00:00Z");
    AiSubscription subscription =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .renewalDate(renewalDate)
            .build();
    PaymentLedger ledger =
        PaymentLedger.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .amount(49000)
            .currency("VND")
            .status(PaymentLedger.Status.succeeded)
            .provider("vnpay")
            .paidAt(Instant.parse("2026-10-01T10:00:00Z"))
            .build();
    PaymentResponse payment =
        PaymentResponse.builder()
            .planCode("PRO")
            .amount(49000)
            .currency("VND")
            .status("succeeded")
            .provider("vnpay")
            .paidAt(ledger.getPaidAt())
            .build();

    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.of(subscription));
    when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
    when(usageRepository.sumRequestCountInWindow(
            eq(userId), any(Instant.class), any(Instant.class)))
        .thenReturn(7L);
    when(paymentLedgerRepository.findFirstByUserIdAndPlanIdAndStatusOrderByPaidAtDesc(
            userId, proPlan.getId(), PaymentLedger.Status.succeeded))
        .thenReturn(Optional.of(ledger));
    when(subscriptionMapper.toUsageResponse(
            eq(7L), eq(proPlan), any(Instant.class), any(Instant.class)))
        .thenReturn(usageResponse);
    when(subscriptionMapper.toPlanSummary(proPlan)).thenReturn(proPlanSummary);
    when(subscriptionMapper.toPaymentResponse(ledger, "PRO")).thenReturn(payment);

    SubscriptionMeResponse result = subscriptionService.getMySubscription(userId);

    assertThat(result.getTier()).isEqualTo("PRO");
    assertThat(result.getStatus()).isEqualTo("active");
    assertThat(result.getRenewalDate()).isEqualTo(renewalDate);
    assertThat(result.getCurrentPlan()).isEqualTo(proPlanSummary);
    assertThat(result.getLatestPayment()).isEqualTo(payment);
    verify(subscriptionMapper).toPaymentResponse(ledger, "PRO");
    verify(planRepository, never()).findById(freePlan.getId());
  }

  @Test
  void getMySubscription_whenPaymentReferencesAnotherPlan_isNotReturned() {
    AiSubscription subscription =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .renewalDate(null)
            .build();

    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.of(subscription));
    when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
    when(usageRepository.sumRequestCountInWindow(
            eq(userId), any(Instant.class), any(Instant.class)))
        .thenReturn(0L);
    when(paymentLedgerRepository.findFirstByUserIdAndPlanIdAndStatusOrderByPaidAtDesc(
            userId, proPlan.getId(), PaymentLedger.Status.succeeded))
        .thenReturn(Optional.empty());
    when(subscriptionMapper.toUsageResponse(
            eq(0L), eq(proPlan), any(Instant.class), any(Instant.class)))
        .thenReturn(usageResponse);
    when(subscriptionMapper.toPlanSummary(proPlan)).thenReturn(proPlanSummary);

    SubscriptionMeResponse result = subscriptionService.getMySubscription(userId);

    assertThat(result.getLatestPayment()).isNull();
    verify(paymentLedgerRepository)
        .findFirstByUserIdAndPlanIdAndStatusOrderByPaidAtDesc(
            userId, proPlan.getId(), PaymentLedger.Status.succeeded);
    verify(planRepository, never()).findById(freePlan.getId());
    verify(subscriptionMapper, never()).toPaymentResponse(any(PaymentLedger.class), any());
  }

  @Test
  void getMySubscription_whenFreePlanMissing_throwsIllegalState() {
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.empty());
    when(planRepository.findByCode("FREE")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> subscriptionService.getMySubscription(userId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Seeded FREE plan not found");

    verifyNoInteractions(usageRepository, paymentLedgerRepository, subscriptionMapper);
  }

  @Test
  void getMySubscription_whenSubscriptionPlanMissing_throwsIllegalState() {
    AiSubscription subscription =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(UUID.randomUUID())
            .status(AiSubscription.Status.active)
            .renewalDate(null)
            .build();
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.of(subscription));
    when(planRepository.findById(subscription.getPlanId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> subscriptionService.getMySubscription(userId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Plan of subscription not found");

    verifyNoInteractions(usageRepository, paymentLedgerRepository, subscriptionMapper);
  }

  @Test
  void getAvailablePlans_mapsRepositoryOrderWithoutWriting() {
    AvailablePlanResponse free =
        AvailablePlanResponse.builder()
            .code("FREE")
            .name("Free")
            .monthlyRequestLimit(20)
            .price(PlanPriceResponse.builder().amount(0).currency("VND").build())
            .build();
    AvailablePlanResponse pro =
        AvailablePlanResponse.builder()
            .code("PRO")
            .name("Pro")
            .monthlyRequestLimit(500)
            .price(PlanPriceResponse.builder().amount(49000).currency("VND").build())
            .build();
    when(planRepository.findByActiveTrueOrderBySortOrderAsc())
        .thenReturn(List.of(freePlan, proPlan));
    when(subscriptionMapper.toAvailablePlan(freePlan)).thenReturn(free);
    when(subscriptionMapper.toAvailablePlan(proPlan)).thenReturn(pro);

    List<AvailablePlanResponse> result = subscriptionService.getAvailablePlans();

    assertThat(result).containsExactly(free, pro);
    verify(subscriptionMapper).toAvailablePlan(freePlan);
    verify(subscriptionMapper).toAvailablePlan(proPlan);
    verify(planRepository, never()).save(any(AiPlan.class));
    // FR-008: the public catalogue never reads usage, subscriptions, or payments.
    verifyNoInteractions(subscriptionRepository, usageRepository, paymentLedgerRepository);
  }

  @Test
  void activatePlan_whenNothingInEffect_insertsActiveRowStartingAtPaidAt() {
    Instant paidAt = Instant.parse("2026-10-05T12:00:00Z");
    when(subscriptionRepository.findInEffectForUpdate(userId)).thenReturn(Optional.empty());
    when(subscriptionRepository.save(any(AiSubscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Optional<AiSubscription> result =
        subscriptionService.activatePlan(userId, proPlan.getId(), paidAt);

    assertThat(result).isPresent();
    AiSubscription saved = result.get();
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getPlanId()).isEqualTo(proPlan.getId());
    assertThat(saved.getStatus()).isEqualTo(AiSubscription.Status.active);
    assertThat(saved.getStartedAt()).isEqualTo(paidAt);
    assertThat(saved.getRenewalDate()).isEqualTo(Instant.parse("2026-11-05T12:00:00Z"));
    assertThat(saved.getExtendedFromId()).isNull();
    verify(subscriptionRepository, never()).existsByUserIdAndPlanIdAndStatus(any(), any(), any());
  }

  @Test
  void activatePlan_whenSamePlanInEffect_insertsScheduledSuccessorFromCurrentRenewal() {
    Instant paidAt = Instant.parse("2026-10-05T12:00:00Z");
    AiSubscription current =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .startedAt(Instant.parse("2026-09-20T00:00:00Z"))
            .renewalDate(Instant.parse("2026-10-20T00:00:00Z"))
            .build();
    when(subscriptionRepository.findInEffectForUpdate(userId)).thenReturn(Optional.of(current));
    when(subscriptionRepository.existsByUserIdAndPlanIdAndStatus(
            userId, proPlan.getId(), AiSubscription.Status.scheduled))
        .thenReturn(false);
    when(subscriptionRepository.save(any(AiSubscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Optional<AiSubscription> result =
        subscriptionService.activatePlan(userId, proPlan.getId(), paidAt);

    assertThat(result).isPresent();
    AiSubscription saved = result.get();
    assertThat(saved.getStatus()).isEqualTo(AiSubscription.Status.scheduled);
    assertThat(saved.getStartedAt()).isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
    assertThat(saved.getRenewalDate()).isEqualTo(Instant.parse("2026-11-20T00:00:00Z"));
    assertThat(saved.getExtendedFromId()).isEqualTo(current.getId());
    assertThat(current.getRenewalDate()).isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
    verify(subscriptionRepository).save(any(AiSubscription.class));
  }

  @Test
  void activatePlan_whenCurrentRenewalMissing_fallsBackToPaidAt() {
    Instant paidAt = Instant.parse("2026-10-05T12:00:00Z");
    AiSubscription current =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .renewalDate(null)
            .build();
    when(subscriptionRepository.findInEffectForUpdate(userId)).thenReturn(Optional.of(current));
    when(subscriptionRepository.existsByUserIdAndPlanIdAndStatus(
            userId, proPlan.getId(), AiSubscription.Status.scheduled))
        .thenReturn(false);
    when(subscriptionRepository.save(any(AiSubscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Optional<AiSubscription> result =
        subscriptionService.activatePlan(userId, proPlan.getId(), paidAt);

    assertThat(result).isPresent();
    assertThat(result.get().getStartedAt()).isEqualTo(paidAt);
    assertThat(result.get().getRenewalDate()).isEqualTo(Instant.parse("2026-11-05T12:00:00Z"));
  }

  @Test
  void activatePlan_whenDifferentPlanInEffect_writesNothingAndReturnsEmpty() {
    Instant paidAt = Instant.parse("2026-10-05T12:00:00Z");
    AiSubscription current =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .renewalDate(Instant.parse("2026-10-20T00:00:00Z"))
            .build();
    when(subscriptionRepository.findInEffectForUpdate(userId)).thenReturn(Optional.of(current));

    Optional<AiSubscription> result =
        subscriptionService.activatePlan(userId, freePlan.getId(), paidAt);

    assertThat(result).isEmpty();
    verify(subscriptionRepository, never()).save(any(AiSubscription.class));
    verify(subscriptionRepository, never()).existsByUserIdAndPlanIdAndStatus(any(), any(), any());
  }

  @Test
  void activatePlan_whenRenewalAlreadyScheduled_writesNothingAndReturnsEmpty() {
    Instant paidAt = Instant.parse("2026-10-05T12:00:00Z");
    AiSubscription current =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .renewalDate(Instant.parse("2026-10-20T00:00:00Z"))
            .build();
    when(subscriptionRepository.findInEffectForUpdate(userId)).thenReturn(Optional.of(current));
    when(subscriptionRepository.existsByUserIdAndPlanIdAndStatus(
            userId, proPlan.getId(), AiSubscription.Status.scheduled))
        .thenReturn(true);

    Optional<AiSubscription> result =
        subscriptionService.activatePlan(userId, proPlan.getId(), paidAt);

    assertThat(result).isEmpty();
    verify(subscriptionRepository, never()).save(any(AiSubscription.class));
  }

  @Test
  void checkPurchaseEligibility_whenNothingInEffect_allowsWithoutLocking() {
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.empty());

    subscriptionService.checkPurchaseEligibility(userId, proPlan.getId());

    verify(subscriptionRepository, never()).findInEffectForUpdate(any());
    verify(subscriptionRepository, never()).save(any(AiSubscription.class));
    verify(subscriptionRepository, never()).existsByUserIdAndPlanIdAndStatus(any(), any(), any());
  }

  @Test
  void checkPurchaseEligibility_whenSamePlanWithoutScheduledRenewal_allowsExtension() {
    AiSubscription current =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .renewalDate(Instant.parse("2026-10-20T00:00:00Z"))
            .build();
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.of(current));
    when(subscriptionRepository.existsByUserIdAndPlanIdAndStatus(
            userId, proPlan.getId(), AiSubscription.Status.scheduled))
        .thenReturn(false);

    subscriptionService.checkPurchaseEligibility(userId, proPlan.getId());

    verify(subscriptionRepository, never()).findInEffectForUpdate(any());
    verify(subscriptionRepository, never()).save(any(AiSubscription.class));
  }

  @Test
  void checkPurchaseEligibility_whenDifferentPlanInEffect_deniesWithConflictMessage() {
    AiSubscription current =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .renewalDate(Instant.parse("2026-10-20T00:00:00Z"))
            .build();
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.of(current));

    assertThatThrownBy(() -> subscriptionService.checkPurchaseEligibility(userId, freePlan.getId()))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Cancel your current subscription before purchasing a different plan");
    verify(subscriptionRepository, never()).findInEffectForUpdate(any());
    verify(subscriptionRepository, never()).save(any(AiSubscription.class));
  }

  @Test
  void checkPurchaseEligibility_whenRenewalAlreadyScheduled_deniesWithConflictMessage() {
    AiSubscription current =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .renewalDate(Instant.parse("2026-10-20T00:00:00Z"))
            .build();
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.of(current));
    when(subscriptionRepository.existsByUserIdAndPlanIdAndStatus(
            userId, proPlan.getId(), AiSubscription.Status.scheduled))
        .thenReturn(true);

    assertThatThrownBy(() -> subscriptionService.checkPurchaseEligibility(userId, proPlan.getId()))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("A renewal is already scheduled for this plan");
    verify(subscriptionRepository, never()).findInEffectForUpdate(any());
    verify(subscriptionRepository, never()).save(any(AiSubscription.class));
  }

  @Test
  void cancelMySubscription_whenRowInEffect_cancelsRowAndCascadesScheduled() {
    AiSubscription current =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .startedAt(Instant.parse("2026-09-20T00:00:00Z"))
            .renewalDate(Instant.parse("2026-10-20T00:00:00Z"))
            .build();
    when(subscriptionRepository.findInEffectForUpdate(userId)).thenReturn(Optional.of(current));
    when(subscriptionRepository.save(any(AiSubscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SubscriptionCancelResponse response = subscriptionService.cancelMySubscription(userId);

    assertThat(response.getStatus()).isEqualTo("cancelled");
    assertThat(response.getCancelledAt()).isNotNull();
    assertThat(current.getStatus()).isEqualTo(AiSubscription.Status.cancelled);
    assertThat(current.getCancelledAt()).isEqualTo(response.getCancelledAt());
    verify(subscriptionRepository)
        .cancelScheduledForUser(
            eq(userId),
            eq(response.getCancelledAt()),
            eq(AiSubscription.Status.cancelled),
            eq(AiSubscription.Status.scheduled));
  }

  @Test
  void cancelMySubscription_whenNothingInEffect_throwsConflictWithoutWriting() {
    when(subscriptionRepository.findInEffectForUpdate(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> subscriptionService.cancelMySubscription(userId))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Subscription already cancelled");
    verify(subscriptionRepository, never()).save(any(AiSubscription.class));
    verify(subscriptionRepository, never()).cancelScheduledForUser(any(), any(), any(), any());
  }

  @Test
  void getMySubscriptionHistory_defaultsToPageZeroSizeTwentySortedByCreatedAtDesc() {
    SubscriptionHistoryRequest request = new SubscriptionHistoryRequest();
    when(subscriptionRepository.findByUserId(eq(userId), any(Pageable.class)))
        .thenAnswer(
            invocation -> new PageImpl<AiSubscription>(List.of(), invocation.getArgument(1), 0));

    PageResponse<SubscriptionHistoryResponse> result =
        subscriptionService.getMySubscriptionHistory(userId, request);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(subscriptionRepository).findByUserId(eq(userId), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(20);
    assertThat(pageable.getSort()).containsExactly(Sort.Order.desc("createdAt"));

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getPage()).isZero();
    assertThat(result.getSize()).isEqualTo(20);
    assertThat(result.getTotalElements()).isZero();
    verify(planRepository).findAllById(Set.of());
    verify(subscriptionRepository, never()).save(any(AiSubscription.class));
    verifyNoInteractions(usageRepository, paymentLedgerRepository, subscriptionMapper);
  }

  @Test
  void getMySubscriptionHistory_mapsRowsWithResolvedPlansAndPreservesPageMetadata() {
    AiSubscription newer =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .startedAt(Instant.parse("2026-10-01T00:00:00Z"))
            .renewalDate(Instant.parse("2026-11-01T00:00:00Z"))
            .createdAt(Instant.parse("2026-10-01T00:00:00Z"))
            .build();
    AiSubscription older =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(freePlan.getId())
            .status(AiSubscription.Status.cancelled)
            .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .cancelledAt(Instant.parse("2026-02-01T00:00:00Z"))
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();
    SubscriptionHistoryResponse newerResponse =
        SubscriptionHistoryResponse.builder().planCode("PRO").status("active").build();
    SubscriptionHistoryResponse olderResponse =
        SubscriptionHistoryResponse.builder().planCode("FREE").status("cancelled").build();

    SubscriptionHistoryRequest request = new SubscriptionHistoryRequest();
    request.setPage(1);
    request.setSize(5);
    when(subscriptionRepository.findByUserId(eq(userId), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(newer, older), PageRequest.of(1, 5), 7));
    when(planRepository.findAllById(Set.of(proPlan.getId(), freePlan.getId())))
        .thenReturn(List.of(proPlan, freePlan));
    when(subscriptionMapper.toHistoryResponse(newer, proPlan)).thenReturn(newerResponse);
    when(subscriptionMapper.toHistoryResponse(older, freePlan)).thenReturn(olderResponse);

    PageResponse<SubscriptionHistoryResponse> result =
        subscriptionService.getMySubscriptionHistory(userId, request);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(subscriptionRepository).findByUserId(eq(userId), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);

    assertThat(result.getContent()).containsExactly(newerResponse, olderResponse);
    assertThat(result.getPage()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(5);
    assertThat(result.getTotalElements()).isEqualTo(7);
    assertThat(result.getTotalPages()).isEqualTo(2);
    verify(planRepository).findAllById(Set.of(proPlan.getId(), freePlan.getId()));
    verify(subscriptionRepository, never()).save(any(AiSubscription.class));
    verifyNoInteractions(usageRepository, paymentLedgerRepository);
  }

  @Test
  void getMySubscriptionHistory_whenPlanMissing_throwsIllegalState() {
    AiSubscription row =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(UUID.randomUUID())
            .status(AiSubscription.Status.active)
            .createdAt(Instant.parse("2026-10-01T00:00:00Z"))
            .build();
    when(subscriptionRepository.findByUserId(eq(userId), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));
    when(planRepository.findAllById(any())).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                subscriptionService.getMySubscriptionHistory(
                    userId, new SubscriptionHistoryRequest()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Plan of subscription not found");

    verifyNoInteractions(subscriptionMapper, usageRepository, paymentLedgerRepository);
  }
}
