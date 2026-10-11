package com.vegalife.unit.infrastructure.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.infrastructure.payment.vnpay.VnpaySigner;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden vectors for VNPay signing (ADR-008): HMAC-SHA512 outputs computed independently with
 * .NET's {@code HMACSHA512} over the exact 2.1.0 canonical forms documented by the gateway, so a
 * refactor of the signer cannot silently change what the sandbox accepts.
 */
class VnpaySignerTest {

  private static final String SECRET = "test-secret-0123456789";

  private static final String PAY_CANONICAL =
      "vnp_Amount=1000000&vnp_Command=pay&vnp_TmnCode=TESTCODE&vnp_TxnRef=abc123";

  private static final String PAY_HASH =
      "f878e75dabb221f32c0f8fa5cb8097027a7319594738c027cb99eb8d2ca57ad8"
          + "94d87ccad391ac5da14786a821ca18847b5bd391c17fd2e32ff35b95e86e4036";

  private static final String QUERY_DR_RAW =
      "12345678|2.1.0|querydr|TESTCODE|abc123|20261004120000|20261004120500"
          + "|127.0.0.1|Upgrade to PRO";

  private static final String QUERY_DR_HASH =
      "b97f7c72d362217daa5271b0ce2fbdc1b4d2ba55f93bd9973797515b5e3636f2"
          + "21f488e57012e9880d8472a864d7fdec5834bc0bf7446d61ad0ab6d14571295f";

  private final VnpaySigner signer = new VnpaySigner(SECRET);

  @Test
  void canonicalizeSortsKeysSkipsEmptyValuesAndExcludesHashFields() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_TxnRef", "abc123");
    params.put("vnp_SecureHashType", "SHA512");
    params.put("vnp_Command", "pay");
    params.put("vnp_BankCode", "");
    params.put("vnp_Amount", "1000000");
    params.put("vnp_SecureHash", "deadbeef");
    params.put("vnp_TmnCode", "TESTCODE");

    assertThat(signer.canonicalize(params)).isEqualTo(PAY_CANONICAL);
  }

  @Test
  void canonicalizeEncodesSpaceAsPlusAndReservedCharactersAsPercent() {
    assertThat(signer.canonicalize(Map.of("vnp_OrderInfo", "Upgrade to PRO")))
        .isEqualTo("vnp_OrderInfo=Upgrade+to+PRO");
    assertThat(signer.canonicalize(Map.of("vnp_ReturnUrl", "http://localhost:3000/payment/result")))
        .isEqualTo("vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A3000%2Fpayment%2Fresult");
  }

  @Test
  void signParamsMatchesFixedPayFormVector() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_TxnRef", "abc123");
    params.put("vnp_TmnCode", "TESTCODE");
    params.put("vnp_Command", "pay");
    params.put("vnp_Amount", "1000000");
    params.put("vnp_SecureHash", "deadbeef");

    assertThat(signer.signParams(params)).isEqualTo(PAY_HASH);
  }

  @Test
  void signRawMatchesPipeJoinedQueryDrVector() {
    assertThat(signer.sign(QUERY_DR_RAW)).isEqualTo(QUERY_DR_HASH);
  }

  @Test
  void verifyParamsAcceptsMatchingSignatureIncludingUppercaseHex() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_Amount", "1000000");
    params.put("vnp_TxnRef", "abc123");
    String hash = signer.signParams(params);

    assertThat(signer.verifyParams(params, hash)).isTrue();
    assertThat(signer.verifyParams(params, hash.toUpperCase(Locale.ROOT))).isTrue();
  }

  @Test
  void verifyParamsRejectsTamperedMissingOrForeignSignatures() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_Amount", "1000000");
    params.put("vnp_TxnRef", "abc123");
    String hash = signer.signParams(params);

    Map<String, String> tampered = new LinkedHashMap<>(params);
    tampered.put("vnp_Amount", "1");
    assertThat(signer.verifyParams(tampered, hash)).isFalse();
    assertThat(signer.verifyParams(params, null)).isFalse();
    assertThat(signer.verifyParams(params, "   ")).isFalse();
    assertThat(signer.verifyParams(params, "not-a-real-hash")).isFalse();
    assertThat(new VnpaySigner("another-secret").verifyParams(params, hash)).isFalse();
  }

  @Test
  void verifyRawAcceptsExactPayloadAndRejectsTampering() {
    String hash = signer.sign(QUERY_DR_RAW);

    assertThat(signer.verifyRaw(QUERY_DR_RAW, hash)).isTrue();
    assertThat(signer.verifyRaw(QUERY_DR_RAW.replace("12345678", "87654321"), hash)).isFalse();
  }

  @Test
  void blankSecretNeverValidatesButNeverThrows() {
    Map<String, String> params = Map.of("vnp_Amount", "1000000");
    String hash = signer.signParams(params);

    assertThat(new VnpaySigner("").verifyParams(params, hash)).isFalse();
    assertThat(new VnpaySigner(null).verifyParams(params, hash)).isFalse();
  }
}
