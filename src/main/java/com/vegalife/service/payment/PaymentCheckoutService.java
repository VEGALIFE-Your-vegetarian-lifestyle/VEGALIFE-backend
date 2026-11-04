package com.vegalife.service.payment;

import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.service.subscription.PlanPurchasePolicy;
import com.vegalife.shared.exception.PaymentGatewayException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts (or resumes) a gateway checkout for a paid AI plan (issue #181, ADR-021).
 *
 * <p>Writes two rows in one transaction: the {@code payment_ledger} row that carries the money, and
 * the {@code ai_subscription} acquisition that carries the plan, with {@code granted_at} null until
 * the gateway confirms. The acquisition must exist before the money moves because the notification
 * echoes only our own transaction reference — nothing in it names a plan — so fulfilment could
 * otherwise not know what was bought.
 *
 * <p>The fulfilment side effects belong to the IPN webhook, so a redirect or a client flag can
 * never grant an allowance. The gateway's secure hash secret never leaves {@link VnpayProperties}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCheckoutService {

  private static final String PROVIDER = "vnpay";

  private final PlanPurchasePolicy planPurchasePolicy;
  private final PaymentLedgerRepository paymentLedgerRepository;
  private final AiSubscriptionRepository subscriptionRepository;
  private final PaymentGateway paymentGateway;
  private final PaymentProperties paymentProperties;
  private final VnpayProperties vnpayProperties;

  /** Everything the caller needs to redirect the member to the gateway. */
  public record CheckoutSession(
      String paymentId,
      String txnRef,
      String paymentUrl,
      long amount,
      String currency,
      String status) {}

  @Transactional
  public CheckoutSession create(UUID userId, AiPlan plan, String ipAddress) {
    ensureGatewayConfigured();
    planPurchasePolicy.requirePurchasable(plan);

    PaymentLedger ledger =
        findReusablePending(userId, plan).orElseGet(() -> createPendingRows(userId, plan));

    String paymentUrl =
        paymentGateway.createPaymentUrl(
            new PaymentGateway.PaymentOrder(
                ledger.getId().toString(),
                ledger.getTxnRef(),
                ledger.getAmount(),
                "Upgrade to " + plan.getCode(),
                ipAddress));

    log.info(
        "Checkout {} for user {} plan {} amount {} {}",
        ledger.getTxnRef(),
        userId,
        plan.getCode(),
        ledger.getAmount(),
        ledger.getCurrency());
    return new CheckoutSession(
        ledger.getId().toString(),
        ledger.getTxnRef(),
        paymentUrl,
        ledger.getAmount(),
        ledger.getCurrency(),
        ledger.getStatus().name());
  }

  private void ensureGatewayConfigured() {
    List<String> missing = new ArrayList<>();
    if (!vnpayProperties.isEnabled()) {
      missing.add("app.payments.vnpay.enabled");
    }
    if (isBlank(vnpayProperties.getTmnCode())) {
      missing.add("app.payments.vnpay.tmn-code");
    }
    if (isBlank(vnpayProperties.getSecureHashSecret())) {
      missing.add("app.payments.vnpay.secure-hash-secret");
    }
    if (isBlank(vnpayProperties.getPaymentUrl())) {
      missing.add("app.payments.vnpay.payment-url");
    }
    if (isBlank(paymentProperties.getReturnUrl())) {
      missing.add("app.payments.return-url");
    }
    if (!missing.isEmpty()) {
      log.error("Checkout refused, payment gateway not configured: missing {}", missing);
      throw new PaymentGatewayException("Payment gateway not configured, missing " + missing);
    }
  }

  /**
   * The plan is no longer on the ledger, so the open session is found through its acquisition. The
   * ledger status is still checked: a failed payment also leaves {@code granted_at} null.
   */
  private Optional<PaymentLedger> findReusablePending(UUID userId, AiPlan plan) {
    Duration ttl = paymentProperties.getCheckoutTtl();
    Instant createdAfter = Instant.now().minus(ttl);
    return subscriptionRepository.findReusablePending(userId, plan.getId(), createdAfter).stream()
        .map(AiSubscription::getPaymentId)
        .flatMap(paymentId -> paymentLedgerRepository.findById(paymentId).stream())
        .filter(ledger -> ledger.getStatus() == PaymentLedgerStatus.pending)
        .findFirst();
  }

  private PaymentLedger createPendingRows(UUID userId, AiPlan plan) {
    PaymentLedger ledger =
        PaymentLedger.builder()
            .userId(userId)
            .amount(plan.getPriceAmount())
            .currency(plan.getPriceCurrency())
            .status(PaymentLedgerStatus.pending)
            .provider(PROVIDER)
            .build();
    ledger = paymentLedgerRepository.saveAndFlush(ledger);
    ledger.setTxnRef(ledger.getId().toString().replace("-", ""));
    ledger = paymentLedgerRepository.save(ledger);

    subscriptionRepository.save(
        AiSubscription.builder()
            .userId(userId)
            .planId(plan.getId())
            .paymentId(ledger.getId())
            .grantedAt(null)
            .build());

    return ledger;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
