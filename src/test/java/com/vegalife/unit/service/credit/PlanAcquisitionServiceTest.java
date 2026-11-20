package com.vegalife.unit.service.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.response.subscription.AcquirePlanResponse;
import com.vegalife.enums.subscription.AcquireAction;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.service.credit.PlanAcquisitionService;
import com.vegalife.service.credit.PlanClaimService;
import com.vegalife.service.credit.RequestAllowanceService;
import com.vegalife.service.payment.PaymentCheckoutService;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanAcquisitionServiceTest {

  private static final String IP = "203.0.113.7";

  @Mock private AiPlanRepository planRepository;
  @Mock private PlanClaimService planClaimService;
  @Mock private PaymentCheckoutService paymentCheckoutService;
  @Mock private RequestAllowanceService requestAllowanceService;

  @InjectMocks private PlanAcquisitionService planAcquisitionService;

  private UUID userId;
  private AiPlan freePlan;
  private AiPlan proPlan;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    freePlan =
        AiPlan.builder()
            .id(UUID.randomUUID())
            .code("FREE")
            .name("Free")
            .requestQuota(5)
            .priceAmount(0L)
            .priceCurrency("VND")
            .active(true)
            .build();
    proPlan =
        AiPlan.builder()
            .id(UUID.randomUUID())
            .code("PRO")
            .name("Pro")
            .requestQuota(500)
            .priceAmount(49000L)
            .priceCurrency("VND")
            .active(true)
            .build();
  }

  @Test
  void acquire_freePlan_claimsWithoutTouchingTheGateway() {
    when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
    when(requestAllowanceService.getRemainingUsage(userId)).thenReturn(5L);

    AcquirePlanResponse response = planAcquisitionService.acquire(userId, "free", IP);

    verify(planClaimService).claimOrThrow(userId, freePlan);
    verify(paymentCheckoutService, never()).create(any(), any(), any());
    assertThat(response.getAction()).isEqualTo(AcquireAction.CLAIMED);
    assertThat(response.getRemainingUsage()).isEqualTo(5L);
    assertThat(response.getPaymentUrl()).isNull();
  }

  @Test
  void acquire_paidPlan_opensCheckoutAndReturnsTheSession() {
    when(planRepository.findByCode("PRO")).thenReturn(Optional.of(proPlan));
    when(paymentCheckoutService.create(userId, proPlan, IP))
        .thenReturn(
            new PaymentCheckoutService.CheckoutSession(
                "pay-1", "txn-1", "https://sandbox.vnpay/pay", 49000L, "VND", "pending"));
    when(requestAllowanceService.getRemainingUsage(userId)).thenReturn(0L);

    AcquirePlanResponse response = planAcquisitionService.acquire(userId, "PRO", IP);

    verify(planClaimService, never()).claimOrThrow(any(), any());
    assertThat(response.getAction()).isEqualTo(AcquireAction.CHECKOUT_CREATED);
    assertThat(response.getPaymentId()).isEqualTo("pay-1");
    assertThat(response.getTxnRef()).isEqualTo("txn-1");
    assertThat(response.getPaymentUrl()).isEqualTo("https://sandbox.vnpay/pay");
    assertThat(response.getAmount()).isEqualTo(49000L);
    assertThat(response.getStatus()).isEqualTo("pending");
  }

  @Test
  void acquire_normalisesThePlanCode() {
    when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
    when(requestAllowanceService.getRemainingUsage(userId)).thenReturn(5L);

    planAcquisitionService.acquire(userId, "  Free  ", IP);

    verify(planRepository).findByCode(eq("FREE"));
  }

  @Test
  void acquire_unknownPlan_is404() {
    when(planRepository.findByCode("NOPE")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> planAcquisitionService.acquire(userId, "nope", IP))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Plan not found");
  }

  @Test
  void acquire_inactivePlan_is400EvenWhenFree() {
    freePlan.setActive(false);
    when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));

    assertThatThrownBy(() -> planAcquisitionService.acquire(userId, "FREE", IP))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Plan is not available");
    verify(planClaimService, never()).claimOrThrow(any(), any());
  }
}
