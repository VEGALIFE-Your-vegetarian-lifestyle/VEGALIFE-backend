package com.vegalife.controller.payment;

import com.vegalife.dto.request.payment.CheckoutRequest;
import com.vegalife.dto.response.payment.CheckoutResponse;
import com.vegalife.service.payment.PaymentCheckoutService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentCheckoutService paymentCheckoutService;

  @PostMapping("/checkout")
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
      @AuthenticationPrincipal UUID userId,
      @Valid @RequestBody CheckoutRequest request,
      HttpServletRequest httpRequest) {
    CheckoutResponse response =
        paymentCheckoutService.checkout(userId, request, clientIp(httpRequest));
    return ResponseEntity.ok(ApiResponse.success(response, "Payment session created"));
  }

  /** First hop of {@code X-Forwarded-For} when behind a proxy, else the socket address. */
  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
