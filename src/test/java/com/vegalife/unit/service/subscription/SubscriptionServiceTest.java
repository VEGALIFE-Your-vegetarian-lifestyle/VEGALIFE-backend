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
import com.vegalife.service.subscription.SubscriptionService;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.empty());
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
    when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.empty());
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

    when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));
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

    when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));
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
    when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.empty());
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
    when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));
    when(planRepository.findById(subscription.getPlanId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> subscriptionService.getMySubscription(userId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Plan of subscription not found");

    verifyNoInteractions(usageRepository, paymentLedgerRepository, subscriptionMapper);
  }
}
