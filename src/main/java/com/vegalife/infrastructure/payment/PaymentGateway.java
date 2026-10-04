package com.vegalife.infrastructure.payment;

import java.time.Instant;
import java.util.Map;

/**
 * Seam between checkout/IPN orchestration and a concrete payment gateway (ADR-008). Amounts
 * crossing this boundary are whole VND; the gateway applies the ×100 wire rule itself. The only
 * implementation today is VNPay — a second gateway would live in its own subpackage.
 */
public interface PaymentGateway {

  /** A single order to sign; the amount is frozen at checkout so replays quote the original. */
  record PaymentOrder(String txnRef, long amountVnd, String orderInfo, String ipAddress) {}

  /** Lookup key for QueryDR: the original creation instant is part of its checksum. */
  record TransactionQuery(String txnRef, Instant createdAt, String orderInfo, String ipAddress) {}

  /** QueryDR outcome with only the fields fulfilment and the sandbox test consume. */
  record QueryResult(
      String responseCode,
      String message,
      String transactionStatus,
      String transactionNo,
      String amount,
      String payDate,
      String bankCode) {}

  /** Signed URL the browser opens to complete the payment. */
  String createPaymentUrl(PaymentOrder order);

  /** True only for a payload whose HMAC-SHA512 signature matches this merchant's secret. */
  boolean verifyCallback(Map<String, String> params);

  /**
   * Server-to-server QueryDR lookup. Implementations throw {@code PaymentGatewayException} on
   * transport or checksum failure — callers must treat that as "untrusted", never "failed".
   */
  QueryResult queryTransaction(TransactionQuery query);
}
