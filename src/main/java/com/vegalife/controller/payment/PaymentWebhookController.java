package com.vegalife.controller.payment;

import com.vegalife.service.payment.PaymentWebhookService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * VNPay IPN webhook (FR-007, FR-020): one exact path served on GET and POST, publicly reachable,
 * answering the gateway's raw {@code {"RspCode","Message"}} acknowledgement instead of the {@code
 * ApiResponse} envelope — always with HTTP 200 so VNPay's retry loop terminates.
 */
@RestController
@RequestMapping("/api/payments/vnpay")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

  private final PaymentWebhookService paymentWebhookService;

  @GetMapping("/ipn")
  public ResponseEntity<Map<String, String>> ipnGet(@RequestParam Map<String, String> params) {
    return handle(params);
  }

  @PostMapping("/ipn")
  public ResponseEntity<Map<String, String>> ipnPost(@RequestParam Map<String, String> params) {
    return handle(params);
  }

  /**
   * Runs fulfilment inside its own transaction, then pins the HTTP 200 contract: any exception
   * (unexpected failure) is swallowed here — after the service proxy has already rolled back — and
   * answered with {@code 99} so VNPay retries. Deliberately catches {@code Exception}: the gateway
   * contract requires this boundary to never fall through to the global handler.
   */
  private ResponseEntity<Map<String, String>> handle(Map<String, String> params) {
    try {
      return ResponseEntity.ok(paymentWebhookService.handleNotification(params));
    } catch (Exception e) {
      log.error("IPN notification failed unexpectedly", e);
      return ResponseEntity.ok(PaymentWebhookService.ack("99", "Unknown error"));
    }
  }
}
