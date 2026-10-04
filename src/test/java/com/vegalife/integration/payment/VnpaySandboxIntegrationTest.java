package com.vegalife.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.infrastructure.payment.PaymentGateway.PaymentOrder;
import com.vegalife.infrastructure.payment.PaymentGateway.QueryResult;
import com.vegalife.infrastructure.payment.PaymentGateway.TransactionQuery;
import com.vegalife.infrastructure.payment.vnpay.VnpayClient;
import com.vegalife.infrastructure.payment.vnpay.VnpayProperties;
import com.vegalife.shared.config.PaymentProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Env-gated round trip against the real VNPay sandbox (ADR-008): a pay URL this client builds must
 * be accepted by {@code sandbox.vnpayment.vn}, and QueryDR for a transaction the merchant has never
 * created must answer {@code 91} (order not found) rather than {@code 97} (invalid checksum), which
 * only holds when both directions of the HMAC are computed the way the gateway computes them. Skips
 * when {@code VNPAY_TMN_CODE} / {@code VNPAY_SECURE_HASH_SECRET} are absent — such a run never
 * proves the gateway exchange, only that the rest of the suite is green.
 */
class VnpaySandboxIntegrationTest {

  private static final String SANDBOX_PAY_URL =
      "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
  private static final String SANDBOX_QUERY_URL =
      "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";

  private VnpayClient gateway;

  @BeforeEach
  void requireSandboxCredentials() {
    String tmnCode = System.getenv("VNPAY_TMN_CODE");
    String secret = System.getenv("VNPAY_SECURE_HASH_SECRET");
    Assumptions.assumeTrue(
        present(tmnCode) && present(secret),
        "VNPAY_TMN_CODE / VNPAY_SECURE_HASH_SECRET not set - skipping sandbox round trip");

    VnpayProperties properties = new VnpayProperties();
    properties.setEnabled(true);
    properties.setTmnCode(tmnCode);
    properties.setSecureHashSecret(secret);
    properties.setPaymentUrl(SANDBOX_PAY_URL);
    properties.setQueryUrl(SANDBOX_QUERY_URL);
    properties.setLocale("vn");

    PaymentProperties paymentProperties = new PaymentProperties();
    paymentProperties.setReturnUrl("https://vegalife.example/payment/return");

    gateway = new VnpayClient(RestClient.builder(), properties, paymentProperties);
  }

  @Test
  void builtPaymentUrl_isAcceptedBySandboxGateway() throws Exception {
    String paymentUrl =
        gateway.createPaymentUrl(
            new PaymentOrder(
                UUID.randomUUID().toString().replace("-", ""),
                49_000L,
                "Vegalife Pro subscription",
                "127.0.0.1"));

    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(paymentUrl))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

    HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

    assertThat(response.statusCode()).isIn(200, 302);
  }

  @Test
  void queryDrForUnknownTransaction_answersOrderNotFoundInsteadOfBadChecksum() {
    QueryResult result =
        gateway.queryTransaction(
            new TransactionQuery(
                UUID.randomUUID().toString().replace("-", ""),
                Instant.now(),
                "Vegalife sandbox lookup",
                "127.0.0.1"));

    assertThat(result.responseCode()).isEqualTo("91");
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
