package com.vegalife.unit.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.subscription.SubscriptionMapper;
import com.vegalife.dto.request.payment.PaymentListRequest;
import com.vegalife.dto.response.payment.PaymentHistoryItemResponse;
import com.vegalife.dto.response.subscription.PlanSummaryResponse;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.service.payment.PaymentHistoryService;
import com.vegalife.shared.dto.PageResponse;
import java.time.Instant;
import java.util.List;
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
class PaymentHistoryServiceTest {

  @Mock private PaymentLedgerRepository paymentLedgerRepository;
  @Mock private AiPlanRepository planRepository;
  @Mock private AiSubscriptionRepository subscriptionRepository;
  @Mock private SubscriptionMapper subscriptionMapper;

  @InjectMocks private PaymentHistoryService paymentHistoryService;

  private UUID userId;
  private AiPlan proPlan;
  private PlanSummaryResponse planSummary;
  private AiSubscription subscription;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    proPlan =
        AiPlan.builder()
            .id(UUID.randomUUID())
            .code("PRO")
            .name("Pro")
            .monthlyRequestLimit(500)
            .priceAmount(49000)
            .priceCurrency("VND")
            .build();
    planSummary =
        PlanSummaryResponse.builder().code("PRO").name("Pro").monthlyRequestLimit(500).build();
    subscription =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .startedAt(Instant.parse("2026-03-01T10:00:00Z"))
            .renewalDate(Instant.parse("2026-04-01T10:00:00Z"))
            .build();
  }

  @Test
  void listMyPayments_scopesQueryToCallerAndSortsNewestFirst() {
    PaymentLedger row = succeededLedger(Instant.parse("2026-03-01T10:00:00Z"));
    when(paymentLedgerRepository.findByUserId(any(UUID.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(row)));
    when(planRepository.findAllById(Set.of(proPlan.getId()))).thenReturn(List.of(proPlan));
    when(subscriptionRepository.findAllById(Set.of(subscription.getId())))
        .thenReturn(List.of(subscription));
    when(subscriptionMapper.toPlanSummary(proPlan)).thenReturn(planSummary);

    PageResponse<PaymentHistoryItemResponse> response =
        paymentHistoryService.listMyPayments(userId, new PaymentListRequest());

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(paymentLedgerRepository).findByUserId(eq(userId), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(20);
    assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));

    assertThat(response.getContent()).hasSize(1);
    PaymentHistoryItemResponse item = response.getContent().getFirst();
    assertThat(item.getId()).isEqualTo(row.getId());
    assertThat(item.getStatus()).isEqualTo("succeeded");
    assertThat(item.getUserId()).isNull();
    assertThat(item.getPlan()).isSameAs(planSummary);
    assertThat(item.getSubscription()).isNotNull();
    assertThat(item.getSubscription().getId()).isEqualTo(subscription.getId());
    assertThat(item.getSubscription().getStatus()).isEqualTo("active");
  }

  @Test
  void listMyPayments_outOfRangePage_returnsEmptyContentNotError() {
    when(paymentLedgerRepository.findByUserId(any(UUID.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(5, 20), 0));

    PageResponse<PaymentHistoryItemResponse> response =
        paymentHistoryService.listMyPayments(userId, new PaymentListRequest());

    assertThat(response.getContent()).isEmpty();
    assertThat(response.getTotalElements()).isZero();
    assertThat(response.getPage()).isEqualTo(5);
  }

  @Test
  void listMyPayments_rowWithoutSubscription_leavesSubscriptionNull() {
    PaymentLedger row =
        PaymentLedger.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .subscriptionId(null)
            .amount(49000L)
            .currency("VND")
            .status(PaymentLedger.Status.failed)
            .createdAt(Instant.parse("2026-03-01T10:00:00Z"))
            .build();
    when(paymentLedgerRepository.findByUserId(any(UUID.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(row)));
    when(planRepository.findAllById(Set.of(proPlan.getId()))).thenReturn(List.of(proPlan));
    when(subscriptionMapper.toPlanSummary(proPlan)).thenReturn(planSummary);

    PageResponse<PaymentHistoryItemResponse> response =
        paymentHistoryService.listMyPayments(userId, new PaymentListRequest());

    PaymentHistoryItemResponse item = response.getContent().getFirst();
    assertThat(item.getSubscription()).isNull();
    assertThat(item.getPlan()).isSameAs(planSummary);
    assertThat(item.getStatus()).isEqualTo("failed");
    verify(subscriptionRepository).findAllById(Set.of());
  }

  @Test
  void listMyPayments_pageRows_batchLoadDistinctIdsOnceEach() {
    Instant newer = Instant.parse("2026-03-02T10:00:00Z");
    Instant older = Instant.parse("2026-03-01T10:00:00Z");
    PaymentLedger first = succeededLedger(newer);
    PaymentLedger second = succeededLedger(older);
    when(paymentLedgerRepository.findByUserId(any(UUID.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(first, second)));
    when(planRepository.findAllById(Set.of(proPlan.getId()))).thenReturn(List.of(proPlan));
    when(subscriptionRepository.findAllById(Set.of(subscription.getId())))
        .thenReturn(List.of(subscription));
    when(subscriptionMapper.toPlanSummary(proPlan)).thenReturn(planSummary);

    paymentHistoryService.listMyPayments(userId, new PaymentListRequest());

    verify(planRepository).findAllById(Set.of(proPlan.getId()));
    verify(subscriptionRepository).findAllById(Set.of(subscription.getId()));
  }

  private PaymentLedger succeededLedger(Instant createdAt) {
    return PaymentLedger.builder()
        .id(UUID.randomUUID())
        .userId(userId)
        .planId(proPlan.getId())
        .subscriptionId(subscription.getId())
        .amount(49000L)
        .currency("VND")
        .status(PaymentLedger.Status.succeeded)
        .provider("vnpay")
        .txnRef("TXN-1")
        .paidAt(createdAt)
        .createdAt(createdAt)
        .build();
  }
}
