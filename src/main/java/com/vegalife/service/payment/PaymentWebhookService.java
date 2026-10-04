package com.vegalife.service.payment;

import com.vegalife.infrastructure.payment.PaymentGateway;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.model.user.User;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.email.EmailService;
import com.vegalife.service.outbound.OutboundEmailPayload;
import com.vegalife.service.subscription.SubscriptionService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VNPay IPN fulfilment (FR-007..FR-017; BR-PAY-001..BR-PAY-008; ADR-008): checksum first, then
 * reference, amount, and a pessimistic lock before any transition — the notification is the single
 * source of truth for whether a payment succeeded (BR-PAY-001).
 *
 * <p>Returns the raw {@code {"RspCode","Message"}} acknowledgement; the caller must keep it off
 * {@code ApiResponse} and inside HTTP 200 (FR-020). A blank gateway secret degrades to {@code 97}
 * inside {@link PaymentGateway#verifyCallback} rather than raising. Unexpected failures propagate
 * so the enclosing transaction rolls back; the controller converts them to {@code 99}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookService {

  private static final String RSP_SUCCESS = "00";
  private static final String RSP_ORDER_NOT_FOUND = "01";
  private static final String RSP_INVALID_AMOUNT = "04";
  private static final String RSP_INVALID_CHECKSUM = "97";
  private static final String RESPONSE_APPROVED = "00";
  private static final String RESPONSE_RISK_FLAGGED = "07";
  private static final String STATUS_PAID = "00";
  private static final String STATUS_PROCESSING = "01";
  private static final String STATUS_REVERSED = "04";
  private static final String PROVIDER = "vnpay";

  private final PaymentGateway paymentGateway;
  private final PaymentLedgerRepository paymentLedgerRepository;
  private final AiPlanRepository planRepository;
  private final UserRepository userRepository;
  private final SubscriptionService subscriptionService;
  private final EmailService emailService;

  /**
   * Processes one IPN notification. Handled outcomes always acknowledge; only exceptions escape
   * (and roll back) for the controller to answer {@code 99}.
   */
  @Transactional
  public Map<String, String> handleNotification(Map<String, String> params) {
    if (!paymentGateway.verifyCallback(params)) {
      log.warn("IPN rejected, checksum mismatch for txnRef {}", params.get("vnp_TxnRef"));
      return ack(RSP_INVALID_CHECKSUM, "Invalid Checksum");
    }
    if (isMissing(params, "vnp_TxnRef")
        || isMissing(params, "vnp_Amount")
        || isMissing(params, "vnp_ResponseCode")
        || isMissing(params, "vnp_TransactionStatus")) {
      log.warn("IPN rejected, required parameter missing for txnRef {}", params.get("vnp_TxnRef"));
      return ack(RSP_INVALID_CHECKSUM, "Invalid Checksum");
    }

    String txnRef = params.get("vnp_TxnRef");
    PaymentLedger ledger = paymentLedgerRepository.findByTxnRef(txnRef).orElse(null);
    if (ledger == null) {
      log.warn("IPN rejected, no payment ledger row for txnRef {}", txnRef);
      return ack(RSP_ORDER_NOT_FOUND, "Order not Found");
    }
    if (!amountMatches(params.get("vnp_Amount"), ledger)) {
      log.warn(
          "IPN rejected, amount mismatch for txnRef {} (notified {}, ledger {})",
          txnRef,
          params.get("vnp_Amount"),
          ledger.getAmount());
      return ack(RSP_INVALID_AMOUNT, "Invalid Amount");
    }

    PaymentLedger locked = paymentLedgerRepository.findByIdForUpdate(ledger.getId()).orElse(null);
    if (locked == null) {
      log.warn("IPN rejected, payment ledger row vanished for txnRef {}", txnRef);
      return ack(RSP_ORDER_NOT_FOUND, "Order not Found");
    }
    return applyOutcome(locked, params);
  }

  /**
   * Builds the gateway acknowledgement body (the one response that is not an {@code ApiResponse}.
   */
  public static Map<String, String> ack(String rspCode, String message) {
    Map<String, String> ack = new LinkedHashMap<>();
    ack.put("RspCode", rspCode);
    ack.put("Message", message);
    return ack;
  }

  private Map<String, String> applyOutcome(PaymentLedger ledger, Map<String, String> params) {
    String responseCode = params.get("vnp_ResponseCode");
    String transactionStatus = params.get("vnp_TransactionStatus");
    boolean paid = RESPONSE_APPROVED.equals(responseCode) && STATUS_PAID.equals(transactionStatus);

    if (ledger.getStatus() != PaymentLedger.Status.pending) {
      logReplay(ledger, paid, responseCode, transactionStatus);
      return ack(RSP_SUCCESS, "Confirm Success");
    }
    if (paid) {
      fulfil(ledger, params);
      return ack(RSP_SUCCESS, "Confirm Success");
    }
    if (STATUS_PROCESSING.equals(transactionStatus)) {
      log.info(
          "IPN: txn {} still processing (responseCode {}), ledger stays pending",
          ledger.getTxnRef(),
          responseCode);
      return ack(RSP_SUCCESS, "Confirm Success");
    }
    recordFailure(ledger, params, responseCode, transactionStatus);
    return ack(RSP_SUCCESS, "Confirm Success");
  }

  /** FR-011/BR-PAY-004: the only branch that mutates anything beyond the ledger itself. */
  private void fulfil(PaymentLedger ledger, Map<String, String> params) {
    Instant paidAt = Instant.now();
    ledger.setStatus(PaymentLedger.Status.succeeded);
    ledger.setPaidAt(paidAt);
    ledger.setProvider(PROVIDER);
    ledger.setProviderReference(params.get("vnp_TransactionNo"));
    ledger.setResponseCode(params.get("vnp_ResponseCode"));
    ledger.setBankCode(params.get("vnp_BankCode"));
    paymentLedgerRepository.save(ledger);

    subscriptionService.activatePlan(ledger.getUserId(), ledger.getPlanId(), paidAt);

    AiPlan plan =
        planRepository
            .findById(ledger.getPlanId())
            .orElseThrow(() -> missingRow("plan", ledger.getPlanId(), ledger.getTxnRef()));
    User user =
        userRepository
            .findById(ledger.getUserId())
            .orElseThrow(() -> missingRow("user", ledger.getUserId(), ledger.getTxnRef()));
    emailService.sendPaymentReceipt(
        user.getEmail(),
        user.getUsername(),
        new OutboundEmailPayload.Receipt(
            plan.getName(), ledger.getAmount(), ledger.getCurrency(), paidAt, ledger.getTxnRef()));

    log.info(
        "IPN: txn {} fulfilled, user {} upgraded to plan {} (amount {} {})",
        ledger.getTxnRef(),
        ledger.getUserId(),
        plan.getCode(),
        ledger.getAmount(),
        ledger.getCurrency());
  }

  /** FR-012/FR-015: ledger recorded as failed, subscription and email untouched. */
  private void recordFailure(
      PaymentLedger ledger,
      Map<String, String> params,
      String responseCode,
      String transactionStatus) {
    ledger.setStatus(PaymentLedger.Status.failed);
    ledger.setResponseCode(responseCode);
    ledger.setBankCode(params.get("vnp_BankCode"));
    paymentLedgerRepository.save(ledger);
    if (RESPONSE_RISK_FLAGGED.equals(responseCode) || STATUS_REVERSED.equals(transactionStatus)) {
      log.error(
          "IPN: txn {} flagged/reversed by gateway, manual review required "
              + "(responseCode {}, transactionStatus {})",
          ledger.getTxnRef(),
          responseCode,
          transactionStatus);
    } else {
      log.warn(
          "IPN: txn {} failed (responseCode {}, transactionStatus {})",
          ledger.getTxnRef(),
          responseCode,
          transactionStatus);
    }
  }

  /** FR-014: terminal rows are never re-fulfilled; a disagreeing payload is only logged. */
  private void logReplay(
      PaymentLedger ledger, boolean paid, String responseCode, String transactionStatus) {
    if ((ledger.getStatus() == PaymentLedger.Status.succeeded) != paid) {
      log.warn(
          "IPN: replay for txn {} disagrees with recorded status {} "
              + "(responseCode {}, transactionStatus {}), no state change",
          ledger.getTxnRef(),
          ledger.getStatus(),
          responseCode,
          transactionStatus);
    } else {
      log.info(
          "IPN: replay for txn {} already {}, no state change",
          ledger.getTxnRef(),
          ledger.getStatus());
    }
  }

  /** FR-010: notified amount (hundredths of VND) must equal the ledger amount exactly. */
  private static boolean amountMatches(String notifiedAmount, PaymentLedger ledger) {
    try {
      return Long.parseLong(notifiedAmount.trim()) / 100L == ledger.getAmount();
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static IllegalStateException missingRow(String kind, UUID id, String txnRef) {
    return new IllegalStateException(
        "Payment " + txnRef + " references unknown " + kind + " " + id);
  }

  private static boolean isMissing(Map<String, String> params, String name) {
    String value = params.get(name);
    return value == null || value.isBlank();
  }
}
