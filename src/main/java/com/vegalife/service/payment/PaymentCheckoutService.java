package com.vegalife.service.payment;

import com.vegalife.dto.request.payment.CheckoutRequest;
import com.vegalife.dto.response.payment.CheckoutResponse;
import com.vegalife.infrastructure.payment.PaymentGateway;
import com.vegalife.infrastructure.payment.vnpay.VnpayProperties;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.shared.config.PaymentProperties;
import com.vegalife.shared.exception.PaymentGatewayException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
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
 * Starts a gateway checkout for an AI plan (FR-001..FR-006, FR-018, FR-019; BR-PAY-005, BR-PAY-009;
 * ADR-008).
 *
 * <p>Writes only {@code payment_ledger} rows: the fulfilment side effects (subscription upsert,
 * receipt) belong to the IPN webhook, so a redirect or client flag can never upgrade an account
 * (BR-PAY-001). The gateway's secure hash secret never leaves {@link VnpayProperties}; the response
 * carries only the signed redirect URL (FR-018).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCheckoutService {

  private static final String PROVIDER = "vnpay";
  private static final String CURRENCY_VND = "VND";

  private final AiPlanRepository planRepository;
  private final PaymentLedgerRepository paymentLedgerRepository;
  private final PaymentGateway paymentGateway;
  private final PaymentProperties paymentProperties;
  private final VnpayProperties vnpayProperties;

  @Transactional
  public CheckoutResponse checkout(UUID userId, CheckoutRequest request, String ipAddress) {
    ensureGatewayConfigured();

    AiPlan plan =
        planRepository
            .findByCode(request.getPlanCode())
            .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
    validatePurchasable(plan);

    PaymentLedger ledger =
        findReusablePending(userId, plan).orElseGet(() -> createPendingRow(userId, plan));

    String paymentUrl =
        paymentGateway.createPaymentUrl(
            new PaymentGateway.PaymentOrder(
                ledger.getTxnRef(), ledger.getAmount(), "Upgrade to " + plan.getCode(), ipAddress));

    log.info(
        "Checkout {} for user {} plan {} amount {} {}",
        ledger.getTxnRef(),
        userId,
        plan.getCode(),
        ledger.getAmount(),
        ledger.getCurrency());
    return CheckoutResponse.builder()
        .txnRef(ledger.getTxnRef())
        .planCode(plan.getCode())
        .amount(ledger.getAmount())
        .currency(ledger.getCurrency())
        .status(ledger.getStatus().name())
        .paymentUrl(paymentUrl)
        .build();
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

  private void validatePurchasable(AiPlan plan) {
    if (!plan.isActive()) {
      throw new ValidationException("Plan is not active");
    }
    if (plan.getPriceAmount() <= 0) {
      throw new ValidationException("Plan is not purchasable: price must be greater than zero");
    }
    if (!CURRENCY_VND.equals(plan.getPriceCurrency())) {
      throw new ValidationException("Plan is not purchasable: only VND plans can be purchased");
    }
  }

  private Optional<PaymentLedger> findReusablePending(UUID userId, AiPlan plan) {
    Duration ttl = paymentProperties.getCheckoutTtl();
    return paymentLedgerRepository
        .findFirstByUserIdAndPlanIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            userId, plan.getId(), PaymentLedger.Status.pending, Instant.now().minus(ttl));
  }

  private PaymentLedger createPendingRow(UUID userId, AiPlan plan) {
    PaymentLedger ledger =
        PaymentLedger.builder()
            .userId(userId)
            .planId(plan.getId())
            .amount(plan.getPriceAmount())
            .currency(plan.getPriceCurrency())
            .status(PaymentLedger.Status.pending)
            .provider(PROVIDER)
            .build();
    ledger = paymentLedgerRepository.saveAndFlush(ledger);
    ledger.setTxnRef(ledger.getId().toString().replace("-", ""));
    return paymentLedgerRepository.save(ledger);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
