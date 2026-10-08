package com.vegalife.unit.infrastructure.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.infrastructure.payment.vnpay.VnpayClient;
import com.vegalife.infrastructure.payment.vnpay.VnpayProperties;
import com.vegalife.infrastructure.payment.vnpay.VnpaySigner;
import com.vegalife.shared.config.PaymentProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verification-path behaviour of {@link VnpayClient} (issue #16 diagnostics): a callback is
 * rejected when the secret is blank and accepted only for the exact canonical payload, and the
 * rejection path never throws so the webhook can still answer {@code 97}.
 */
class VnpayClientVerifyCallbackTest {

  private static VnpayClient client(String secret) {
    VnpayProperties properties = new VnpayProperties();
    properties.setTmnCode("TESTCODE");
    properties.setSecureHashSecret(secret);
    properties.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
    properties.setLocale("vn");
    return new VnpayClient(properties, new PaymentProperties());
  }

  private static Map<String, String> ipnPayload() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_TxnRef", "222222p");
    params.put("vnp_Amount", "4900000");
    params.put("vnp_ResponseCode", "00");
    params.put("vnp_TransactionStatus", "00");
    params.put("vnp_TransactionNo", "14093211");
    params.put("vnp_BankCode", "NCB");
    params.put("vnp_OrderInfo", "Upgrade to PRO");
    return params;
  }

  @Test
  void rejectsWhenSecretIsBlank() {
    Map<String, String> params = ipnPayload();
    params.put(VnpaySigner.SECURE_HASH_FIELD, "any-non-blank-hash-value");

    assertThat(client("").verifyCallback(params)).isFalse();
  }

  @Test
  void acceptsPayloadSignedWithTheConfiguredSecret() {
    Map<String, String> params = ipnPayload();
    params.put(VnpaySigner.SECURE_HASH_FIELD, new VnpaySigner("shared-secret").signParams(params));

    assertThat(client("shared-secret").verifyCallback(params)).isTrue();
  }

  @Test
  void rejectsPayloadSignedWithADifferentSecret() {
    Map<String, String> params = ipnPayload();
    params.put(VnpaySigner.SECURE_HASH_FIELD, new VnpaySigner("other-secret").signParams(params));

    assertThat(client("shared-secret").verifyCallback(params)).isFalse();
  }

  @Test
  void returnsFalseForNullPayloadInsteadOfThrowing() {
    assertThat(client("shared-secret").verifyCallback(null)).isFalse();
  }
}
