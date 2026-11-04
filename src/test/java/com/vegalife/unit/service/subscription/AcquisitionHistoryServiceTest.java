package com.vegalife.unit.service.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.subscription.SubscriptionHistoryRequest;
import com.vegalife.dto.response.subscription.SubscriptionHistoryResponse;
import com.vegalife.enums.subscription.AcquisitionSource;
import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.service.subscription.AcquisitionHistoryService;
import com.vegalife.shared.dto.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AcquisitionHistoryServiceTest {

  @Mock private AiSubscriptionRepository subscriptionRepository;
  @Mock private AiPlanRepository planRepository;
  @Mock private PaymentLedgerRepository paymentLedgerRepository;

  @InjectMocks private AcquisitionHistoryService acquisitionHistoryService;

  private UUID userId;
  private AiPlan proPlan;
  private AiPlan freePlan;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    proPlan =
        AiPlan.builder().id(UUID.randomUUID()).code("PRO").name("Pro").requestQuota(500).build();
    freePlan =
        AiPlan.builder().id(UUID.randomUUID()).code("FREE").name("Free").requestQuota(5).build();
  }

  @Test
  void getMyAcquisitions_mapsAClaimAndAPurchaseFromOnePage() {
    Instant grantedAt = Instant.parse("2026-03-01T10:00:00Z");
    AiSubscription claim =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(freePlan.getId())
            .paymentId(null)
            .grantedAt(grantedAt)
            .createdAt(grantedAt)
            .build();
    PaymentLedger payment =
        PaymentLedger.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .amount(49000L)
            .currency("VND")
            .status(PaymentLedgerStatus.succeeded)
            .build();
    AiSubscription purchase =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(proPlan.getId())
            .paymentId(payment.getId())
            .grantedAt(grantedAt)
            .createdAt(grantedAt)
            .build();

    when(subscriptionRepository.findByUserId(any(UUID.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(claim, purchase)));
    when(planRepository.findAllById(Set.of(freePlan.getId(), proPlan.getId())))
        .thenReturn(List.of(freePlan, proPlan));
    when(paymentLedgerRepository.findAllById(Set.of(payment.getId()))).thenReturn(List.of(payment));

    PageResponse<SubscriptionHistoryResponse> response =
        acquisitionHistoryService.getMyAcquisitions(userId, new SubscriptionHistoryRequest());

    assertThat(response.getContent()).hasSize(2);

    SubscriptionHistoryResponse claimRow = response.getContent().getFirst();
    assertThat(claimRow.getPlanCode()).isEqualTo("FREE");
    assertThat(claimRow.getSource()).isEqualTo(AcquisitionSource.CLAIM);
    assertThat(claimRow.getPaymentId()).isNull();
    assertThat(claimRow.getAmount()).isNull();
    assertThat(claimRow.getStatus()).isNull();
    assertThat(claimRow.getGrantedAt()).isEqualTo(grantedAt);

    SubscriptionHistoryResponse purchaseRow = response.getContent().get(1);
    assertThat(purchaseRow.getPlanCode()).isEqualTo("PRO");
    assertThat(purchaseRow.getSource()).isEqualTo(AcquisitionSource.PURCHASE);
    assertThat(purchaseRow.getPaymentId()).isEqualTo(payment.getId());
    assertThat(purchaseRow.getAmount()).isEqualTo(49000L);
    assertThat(purchaseRow.getCurrency()).isEqualTo("VND");
    assertThat(purchaseRow.getStatus()).isEqualTo("succeeded");
  }

  @Test
  void getMyAcquisitions_skipsThePaymentBatchLoadWhenNothingWasBought() {
    AiSubscription claim =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(freePlan.getId())
            .grantedAt(Instant.parse("2026-03-01T10:00:00Z"))
            .createdAt(Instant.parse("2026-03-01T10:00:00Z"))
            .build();
    when(subscriptionRepository.findByUserId(any(UUID.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(claim)));
    when(planRepository.findAllById(Set.of(freePlan.getId()))).thenReturn(List.of(freePlan));

    PageResponse<SubscriptionHistoryResponse> response =
        acquisitionHistoryService.getMyAcquisitions(userId, new SubscriptionHistoryRequest());

    assertThat(response.getContent()).hasSize(1);
    assertThat(response.getContent().getFirst().getSource()).isEqualTo(AcquisitionSource.CLAIM);
  }
}
