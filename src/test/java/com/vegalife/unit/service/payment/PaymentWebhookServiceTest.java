package com.vegalife.unit.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.model.user.User;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.email.EmailService;
import com.vegalife.service.payment.PaymentGateway;
import com.vegalife.service.payment.PaymentWebhookService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The IPN is the only path that grants an allowance (issue #181). It finds the acquisition written
 * at checkout by payment id and stamps {@code granted_at}; there is no balance to credit and no
 * gate to consult.
 */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

  @Mock private PaymentGateway paymentGateway;
  @Mock private PaymentLedgerRepository paymentLedgerRepository;
  @Mock private AiSubscriptionRepository subscriptionRepository;
  @Mock private AiPlanRepository planRepository;
  @Mock private UserRepository userRepository;
  @Mock private EmailService emailService;

  @InjectMocks private PaymentWebhookService paymentWebhookService;

  private UUID userId;
  private UUID planId;
  private String txnRef;
  private PaymentLedger ledger;
  private AiSubscription acquisition;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    planId = UUID.randomUUID();
    txnRef = "TXN-111";
    ledger =
        PaymentLedger.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .amount(49000L)
            .currency("VND")
            .status(PaymentLedgerStatus.pending)
            .txnRef(txnRef)
            .build();
    acquisition =
        AiSubscription.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .planId(planId)
            .paymentId(ledger.getId())
            .grantedAt(null)
            .build();
  }

  @Test
  void handleNotification_paid_stampsGrantedAtOnTheAcquisitionAndSendsReceipt() {
    Map<String, String> params = approvedParams();
    stubHappyPath(params);

    Map<String, String> ack = paymentWebhookService.handleNotification(params);

    assertThat(ack.get("RspCode")).isEqualTo("00");
    assertThat(ledger.getStatus()).isEqualTo(PaymentLedgerStatus.succeeded);

    ArgumentCaptor<AiSubscription> saved = ArgumentCaptor.forClass(AiSubscription.class);
    verify(subscriptionRepository).save(saved.capture());
    assertThat(saved.getValue().getGrantedAt()).isNotNull();
    assertThat(saved.getValue().getPaymentId()).isEqualTo(ledger.getId());
    verify(emailService).sendPaymentReceipt(eq("member@example.com"), eq("member"), any());
  }

  @Test
  void handleNotification_paidTwice_isIdempotentBecauseTheLedgerIsNoLongerPending() {
    Map<String, String> params = approvedParams();
    stubHappyPath(params);

    paymentWebhookService.handleNotification(params);
    paymentWebhookService.handleNotification(params);

    // The acquisition is stamped once: the replay hits the non-pending branch and returns early.
    verify(subscriptionRepository, org.mockito.Mockito.times(1)).save(any(AiSubscription.class));
  }

  @Test
  void handleNotification_failedPayment_neverTouchesTheAcquisition() {
    Map<String, String> params = approvedParams();
    params.put("vnp_ResponseCode", "01");
    when(paymentGateway.verifyCallback(params)).thenReturn(true);
    when(paymentLedgerRepository.findByTxnRef(txnRef)).thenReturn(Optional.of(ledger));
    when(paymentLedgerRepository.findByIdForUpdate(ledger.getId())).thenReturn(Optional.of(ledger));
    when(paymentLedgerRepository.save(any(PaymentLedger.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Map<String, String> ack = paymentWebhookService.handleNotification(params);

    assertThat(ack.get("RspCode")).isEqualTo("00");
    assertThat(ledger.getStatus()).isEqualTo(PaymentLedgerStatus.failed);
    assertThat(acquisition.getGrantedAt()).isNull();
    verifyNoInteractions(subscriptionRepository, planRepository, userRepository, emailService);
  }

  @Test
  void handleNotification_paidWithoutAcquisition_failsSoTheGatewayRetries() {
    Map<String, String> params = approvedParams();
    when(paymentGateway.verifyCallback(params)).thenReturn(true);
    when(paymentLedgerRepository.findByTxnRef(txnRef)).thenReturn(Optional.of(ledger));
    when(paymentLedgerRepository.findByIdForUpdate(ledger.getId())).thenReturn(Optional.of(ledger));
    when(paymentLedgerRepository.save(any(PaymentLedger.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(subscriptionRepository.findByPaymentId(ledger.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> paymentWebhookService.handleNotification(params))
        .isInstanceOf(IllegalStateException.class);
  }

  private void stubHappyPath(Map<String, String> params) {
    AiPlan plan =
        AiPlan.builder()
            .id(planId)
            .code("PRO")
            .name("Pro")
            .requestQuota(500)
            .priceAmount(49000L)
            .priceCurrency("VND")
            .build();
    User user = User.builder().id(userId).displayName("member").email("member@example.com").build();
    when(paymentGateway.verifyCallback(params)).thenReturn(true);
    when(paymentLedgerRepository.findByTxnRef(txnRef)).thenReturn(Optional.of(ledger));
    when(paymentLedgerRepository.findByIdForUpdate(ledger.getId())).thenReturn(Optional.of(ledger));
    when(paymentLedgerRepository.save(any(PaymentLedger.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(subscriptionRepository.findByPaymentId(ledger.getId()))
        .thenReturn(Optional.of(acquisition));
    when(subscriptionRepository.save(any(AiSubscription.class)))
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
