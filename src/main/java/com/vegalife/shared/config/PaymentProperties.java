package com.vegalife.shared.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Purchase-payment tuning, bound from {@code app.payments.*} (ADR-008).
 *
 * <p>Checkout fails fast (FR-019) while {@code enabled} is false or any of {@code tmnCode}, {@code
 * secureHashSecret} or {@code returnUrl} is blank — the gateway URLs are deliberately
 * profile-scoped: only {@code application-dev.yml} ships the sandbox endpoints, so production must
 * set its own (with {@code VNPAY_TMN_CODE} / {@code VNPAY_SECURE_HASH_SECRET}) before a checkout
 * can start. The webhook path never depends on this being complete: a missing secure hash still
 * lets the endpoint answer a signed payload with {@code 97} rather than falling through to the
 * global exception handler (FR-020).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.payments")
public class PaymentProperties {

  /** How long a pending checkout row is reused before a fresh txn ref is minted. */
  private Duration checkoutTtl = Duration.ofMinutes(30);

  /** Frontend page the gateway redirects the browser back to; never an API path. */
  private String returnUrl = "";

  private Vnpay vnpay = new Vnpay();

  @Data
  public static class Vnpay {

    /** Kill switch for the checkout endpoint only; the webhook stays reachable. */
    private boolean enabled = false;

    private String tmnCode = "";

    private String secureHashSecret = "";

    private String paymentUrl = "";

    private String queryUrl = "";

    private String locale = "vn";
  }
}
