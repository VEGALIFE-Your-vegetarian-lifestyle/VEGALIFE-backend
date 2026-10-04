package com.vegalife.infrastructure.payment.vnpay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vegalife.infrastructure.payment.PaymentGateway;
import com.vegalife.shared.config.PaymentProperties;
import com.vegalife.shared.exception.PaymentGatewayException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The single {@link PaymentGateway} implementation: hand-rolled VNPay 2.1.0 client (ADR-008, Option
 * B — a thin adapter instead of an upstream SDK). Builds signed pay redirects, verifies IPN
 * payloads, and speaks QueryDR; all signing lives in {@link VnpaySigner}.
 *
 * <p>Checkout is expected to fail fast before ever reaching here (FR-019: gateway disabled or
 * credentials blank → 500), while the IPN path only needs {@link #verifyCallback(Map)}, which
 * degrades to "signature mismatch" under a blank secret (FR-020).
 */
@Component
public class VnpayClient implements PaymentGateway {

  private static final String API_VERSION = "2.1.0";
  private static final String COMMAND_PAY = "pay";
  private static final String COMMAND_QUERY_DR = "querydr";
  private static final String CURR_CODE = "VND";
  private static final String ORDER_TYPE = "other";
  private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
  private static final int READ_TIMEOUT_MILLIS = 15_000;

  /** Gateway timestamps are GMT+7 regardless of server locale; {@code vnp_CreateDate} et al. */
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.ofHours(7));

  private final RestClient restClient;
  private final VnpayProperties properties;
  private final PaymentProperties paymentProperties;
  private final VnpaySigner signer;

  public VnpayClient(
      RestClient.Builder restClientBuilder,
      VnpayProperties properties,
      PaymentProperties paymentProperties) {
    this.properties = properties;
    this.paymentProperties = paymentProperties;
    this.signer = new VnpaySigner(properties.getSecureHashSecret());
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
    requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
    this.restClient = restClientBuilder.requestFactory(requestFactory).build();
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
    params.put("vnp_ReturnUrl", paymentProperties.getReturnUrl());
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
    return signer.verifyParams(params, params.get(VnpaySigner.SECURE_HASH_FIELD));
  }

  @Override
  public QueryResult queryTransaction(TransactionQuery query) {
    String createdAt = TIMESTAMP.format(query.createdAt());
    String requestId = UUID.randomUUID().toString().replace("-", "");
    Map<String, String> body = new LinkedHashMap<>();
    body.put("vnp_RequestId", requestId);
    body.put("vnp_Version", API_VERSION);
    body.put("vnp_Command", COMMAND_QUERY_DR);
    body.put("vnp_TmnCode", properties.getTmnCode());
    body.put("vnp_TxnRef", query.txnRef());
    body.put("vnp_TransactionDate", createdAt);
    body.put("vnp_CreateDate", createdAt);
    body.put("vnp_IpAddr", query.ipAddress());
    body.put("vnp_OrderInfo", query.orderInfo());
    body.put(
        VnpaySigner.SECURE_HASH_FIELD,
        signer.sign(
            String.join(
                "|",
                requestId,
                API_VERSION,
                COMMAND_QUERY_DR,
                properties.getTmnCode(),
                query.txnRef(),
                createdAt,
                createdAt,
                query.ipAddress(),
                query.orderInfo())));

    QueryDrResponse response;
    try {
      response =
          restClient
              .post()
              .uri(properties.getQueryUrl())
              .header("Content-Type", "Application/json")
              .body(body)
              .retrieve()
              .body(QueryDrResponse.class);
    } catch (RestClientException | IllegalArgumentException e) {
      throw new PaymentGatewayException("QueryDR transport failure for txn " + query.txnRef(), e);
    }
    if (response == null) {
      throw new PaymentGatewayException("QueryDR returned no body for txn " + query.txnRef());
    }
    String payload = responseSignature(response);
    if (!signer.verifyRaw(payload, response.vnp_SecureHash())) {
      throw new PaymentGatewayException(
          "QueryDR response checksum mismatch for txn " + query.txnRef());
    }
    return new QueryResult(
        nullToEmpty(response.vnp_ResponseCode()),
        nullToEmpty(response.vnp_Message()),
        nullToEmpty(response.vnp_TransactionStatus()),
        nullToEmpty(response.vnp_TransactionNo()),
        nullToEmpty(response.vnp_Amount()),
        nullToEmpty(response.vnp_PayDate()),
        nullToEmpty(response.vnp_BankCode()));
  }

  private static String responseSignature(QueryDrResponse r) {
    return String.join(
        "|",
        nullToEmpty(r.vnp_ResponseId()),
        nullToEmpty(r.vnp_Command()),
        nullToEmpty(r.vnp_ResponseCode()),
        nullToEmpty(r.vnp_Message()),
        nullToEmpty(r.vnp_TmnCode()),
        nullToEmpty(r.vnp_TxnRef()),
        nullToEmpty(r.vnp_Amount()),
        nullToEmpty(r.vnp_BankCode()),
        nullToEmpty(r.vnp_PayDate()),
        nullToEmpty(r.vnp_TransactionNo()),
        nullToEmpty(r.vnp_TransactionType()),
        nullToEmpty(r.vnp_TransactionStatus()),
        nullToEmpty(r.vnp_OrderInfo()),
        nullToEmpty(r.vnp_PromotionCode()),
        nullToEmpty(r.vnp_PromotionAmount()));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /** QueryDR reply; unknown fields are ignored and nulls hash as empty per the 2.1.0 sample. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record QueryDrResponse(
      String vnp_ResponseId,
      String vnp_Command,
      String vnp_ResponseCode,
      String vnp_Message,
      String vnp_TmnCode,
      String vnp_TxnRef,
      String vnp_Amount,
      String vnp_BankCode,
      String vnp_PayDate,
      String vnp_TransactionNo,
      String vnp_TransactionType,
      String vnp_TransactionStatus,
      String vnp_OrderInfo,
      String vnp_PromotionCode,
      String vnp_PromotionAmount,
      String vnp_SecureHash) {}
}
