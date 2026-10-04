package com.vegalife.infrastructure.payment.vnpay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VNPay merchant credentials and endpoints, bound from {@code app.payments.vnpay.*} (ADR-008).
 *
 * <p>Fail-closed defaults ({@code enabled=false}, blank credentials/URLs) keep checkout answering
 * 500 per FR-019 until a profile fills them in; only {@code application-dev.yml} ships the sandbox
 * block today, so production must supply its own via {@code VNPAY_TMN_CODE} / {@code
 * VNPAY_SECURE_HASH_SECRET}. The webhook is independent of this block being complete: without a
 * secret every signature check fails and it acks {@code 97} (FR-020) instead of throwing.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.payments.vnpay")
public class VnpayProperties {

  /** Kill switch for the checkout endpoint only; the webhook stays reachable. */
  private boolean enabled = false;

  private String tmnCode = "";

  private String secureHashSecret = "";

  private String paymentUrl = "";

  private String locale = "vn";
}
