package com.vegalife.infrastructure.payment.vnpay;

import com.vegalife.infrastructure.payment.PaymentGateway;
import com.vegalife.shared.config.PaymentProperties;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The single {@link PaymentGateway} implementation: hand-rolled VNPay 2.1.0 client (ADR-008, Option
 * B — a thin adapter instead of an upstream SDK). Builds signed pay redirects and verifies IPN
 * payloads; all signing lives in {@link VnpaySigner}.
 *
 * <p>Checkout is expected to fail fast before ever reaching here (FR-019: gateway disabled or
 * credentials blank → 500), while the IPN path only needs {@link #verifyCallback(Map)}, which
 * degrades to "signature mismatch" under a blank secret (FR-020).
 */
@Component
@Slf4j
public class VnpayClient implements PaymentGateway {

  private static final String API_VERSION = "2.1.0";
  private static final String COMMAND_PAY = "pay";
  private static final String CURR_CODE = "VND";
  private static final String ORDER_TYPE = "other";

  /** Gateway timestamps are GMT+7 regardless of server locale; {@code vnp_CreateDate} et al. */
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.ofHours(7));

  private final VnpayProperties properties;
  private final PaymentProperties paymentProperties;
  private final VnpaySigner signer;

  public VnpayClient(VnpayProperties properties, PaymentProperties paymentProperties) {
    this.properties = properties;
    this.paymentProperties = paymentProperties;
    this.signer = new VnpaySigner(properties.getSecureHashSecret());
  }

  @Override
  public String createPaymentUrl(PaymentOrder order) {
    Instant now = Instant.now();
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_Version", API_VERSION);
    params.put("vnp_Command", COMMAND_PAY);
    params.put("vnp_TmnCode", properties.getTmnCode());
    params.put("vnp_CurrCode", CURR_CODE);
    params.put("vnp_Locale", properties.getLocale());
    params.put("vnp_TxnRef", order.txnRef());
    params.put("vnp_OrderInfo", order.orderInfo());
    params.put("vnp_OrderType", ORDER_TYPE);
    params.put("vnp_Amount", String.valueOf(order.amountVnd() * 100L));
    params.put("vnp_ReturnUrl", returnUrlWithId(order.paymentId()));
    params.put("vnp_IpAddr", order.ipAddress());
    params.put("vnp_CreateDate", TIMESTAMP.format(now));
    params.put("vnp_ExpireDate", TIMESTAMP.format(now.plus(paymentProperties.getCheckoutTtl())));
    String canonical = signer.canonicalize(params);
    return properties.getPaymentUrl()
        + "?"
        + canonical
        + "&"
        + VnpaySigner.SECURE_HASH_FIELD
        + "="
        + signer.sign(canonical);
  }

  @Override
  public boolean verifyCallback(Map<String, String> params) {
    if (params == null) {
      return false;
    }
    boolean valid = signer.verifyParams(params, params.get(VnpaySigner.SECURE_HASH_FIELD));
    if (!valid) {
      // Safe diagnostics: the canonical form holds only public gateway fields and the configured
      // secret is never logged. Distinguishes a missing secret (config fault) from a real
      // signature/encoding mismatch on a captured payload.
      log.warn(
          "VNPay callback rejected: secretConfigured={}, tmnCode={}, canonical=[{}]",
          signer.hasSecret(),
          properties.getTmnCode(),
          signer.canonicalize(params));
    }
    return valid;
  }

  /**
   * Configured {@code return-url} base with the ledger id appended as a path segment, so the
   * frontend result page learns which payment the redirect belongs to without trusting query
   * parameters.
   */
  private String returnUrlWithId(String paymentId) {
    String base = paymentProperties.getReturnUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/" + paymentId;
  }
}
