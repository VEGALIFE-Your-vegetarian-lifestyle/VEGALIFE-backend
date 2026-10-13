package com.vegalife.unit.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.infrastructure.payment.PaymentGateway;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.model.user.User;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.email.EmailService;
import com.vegalife.service.payment.PaymentWebhookService;
import com.vegalife.service.subscription.SubscriptionService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

  @Mock private PaymentGateway paymentGateway;
  @Mock private PaymentLedgerRepository paymentLedgerRepository;
  @Mock private AiPlanRepository planRepository;
  @Mock private UserRepository userRepository;
  @Mock private SubscriptionService subscriptionService;
  @Mock private EmailService emailService;

  @InjectMocks private PaymentWebhookService paymentWebhookService;

  private UUID userId;
  private UUID planId;
  private String txnRef;
  private PaymentLedger ledger;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    planId = UUID.randomUUID();
    txnRef = "TXN-111";
    ledger =
        PaymentLedger.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(planId)
            .amount(49000L)
            .currency("VND")
            .status(PaymentLedger.Status.pending)
            .txnRef(txnRef)
            .build();
  }

  @Test
  void handleNotification_paidWithGateOpen_recordsSubscriptionIdOnLedger() {
    Map<String, String> params = approvedParams();
    AiSubscription subscription =
        AiSubscription.builder().id(UUID.randomUUID()).userId(userId).planId(planId).build();
    stubHappyPath(params);
    when(subscriptionService.activatePlan(eq(userId), eq(planId), any(Instant.class)))
        .thenReturn(Optional.of(subscription));

    Map<String, String> ack = paymentWebhookService.handleNotification(params);

    assertThat(ack.get("RspCode")).isEqualTo("00");
    assertThat(ledger.getStatus()).isEqualTo(PaymentLedger.Status.succeeded);
    assertThat(ledger.getSubscriptionId()).isEqualTo(subscription.getId());
    verify(emailService).sendPaymentReceipt(eq("member@example.com"), eq("member"), any());
  }

  @Test
  void handleNotification_paidWithGateDenying_leavesSubscriptionIdNull() {
    Map<String, String> params = approvedParams();
    stubHappyPath(params);
    when(subscriptionService.activatePlan(eq(userId), eq(planId), any(Instant.class)))
        .thenReturn(Optional.empty());

    Map<String, String> ack = paymentWebhookService.handleNotification(params);

    assertThat(ack.get("RspCode")).isEqualTo("00");
    assertThat(ledger.getStatus()).isEqualTo(PaymentLedger.Status.succeeded);
    assertThat(ledger.getSubscriptionId()).isNull();
  }

  @Test
  void handleNotification_failedPayment_neverActivatesPlanAndKeepsSubscriptionIdNull() {
    Map<String, String> params = approvedParams();
    params.put("vnp_ResponseCode", "01");
    when(paymentGateway.verifyCallback(params)).thenReturn(true);
    when(paymentLedgerRepository.findByTxnRef(txnRef)).thenReturn(Optional.of(ledger));
    when(paymentLedgerRepository.findByIdForUpdate(ledger.getId())).thenReturn(Optional.of(ledger));
    when(paymentLedgerRepository.save(any(PaymentLedger.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Map<String, String> ack = paymentWebhookService.handleNotification(params);

    assertThat(ack.get("RspCode")).isEqualTo("00");
    assertThat(ledger.getStatus()).isEqualTo(PaymentLedger.Status.failed);
    assertThat(ledger.getSubscriptionId()).isNull();
    verifyNoInteractions(subscriptionService, planRepository, userRepository, emailService);
  }

  private void stubHappyPath(Map<String, String> params) {
    AiPlan plan =
        AiPlan.builder()
            .id(planId)
            .code("PRO")
            .name("Pro")
            .priceAmount(49000L)
            .priceCurrency("VND")
            .build();
    User user = User.builder().id(userId).username("member").email("member@example.com").build();
    when(paymentGateway.verifyCallback(params)).thenReturn(true);
    when(paymentLedgerRepository.findByTxnRef(txnRef)).thenReturn(Optional.of(ledger));
    when(paymentLedgerRepository.findByIdForUpdate(ledger.getId())).thenReturn(Optional.of(ledger));
    when(paymentLedgerRepository.save(any(PaymentLedger.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
  }

  private Map<String, String> approvedParams() {
    Map<String, String> params = new HashMap<>();
    params.put("vnp_TxnRef", txnRef);
    params.put("vnp_Amount", String.valueOf(ledger.getAmount() * 100L));
    params.put("vnp_ResponseCode", "00");
    params.put("vnp_TransactionStatus", "00");
    params.put("vnp_TransactionNo", "14112345");
    params.put("vnp_BankCode", "NCB");
    return params;
  }
}
