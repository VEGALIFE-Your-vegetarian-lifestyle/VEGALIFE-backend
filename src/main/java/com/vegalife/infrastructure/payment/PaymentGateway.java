package com.vegalife.infrastructure.payment;

import java.util.Map;

/**
 * Seam between checkout/IPN orchestration and a concrete payment gateway (ADR-008). Amounts
 * crossing this boundary are whole VND; the gateway applies the ×100 wire rule itself. The only
 * implementation today is VNPay — a second gateway would live in its own subpackage.
 */
public interface PaymentGateway {

  /**
   * A single order to sign; the amount is frozen at checkout so replays quote the original. {@code
   * paymentId} (the ledger row id) rides along so the gateway can append it as the return URL's
   * path segment — the frontend reads it back after the redirect.
   */
  record PaymentOrder(
      String paymentId, String txnRef, long amountVnd, String orderInfo, String ipAddress) {}

  /** Signed URL the browser opens to complete the payment. */
  String createPaymentUrl(PaymentOrder order);

  /** True only for a payload whose HMAC-SHA512 signature matches this merchant's secret. */
  boolean verifyCallback(Map<String, String> params);
}
