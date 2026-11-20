package com.vegalife.controller.payment;

import com.vegalife.dto.request.payment.PaymentListRequest;
import com.vegalife.dto.response.payment.PaymentHistoryItemResponse;
import com.vegalife.dto.response.payment.PaymentStatusResponse;
import com.vegalife.service.payment.PaymentHistoryService;
import com.vegalife.service.payment.PaymentStatusService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The member's money records (issue #181, ADR-021).
 *
 * <p>Checkout moved to {@code POST /api/subscriptions/acquire}, because acquiring a plan is one
 * decision and the price decides whether a payment is involved. These two endpoints stay
 * deliberately money-only: {@code payment_ledger} no longer identifies a plan, and duplicating the
 * acquisition history here would give the same fact two sources of truth.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PaymentController {

  private final PaymentStatusService paymentStatusService;
  private final PaymentHistoryService paymentHistoryService;

  /** Exact-path route; Spring MVC resolves it ahead of the {@code /{paymentId}} template. */
  @GetMapping
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<PageResponse<PaymentHistoryItemResponse>>> listMyPayments(
      @AuthenticationPrincipal UUID userId,
      @Valid @ParameterObject @ModelAttribute PaymentListRequest request) {
    PageResponse<PaymentHistoryItemResponse> response =
        paymentHistoryService.listMyPayments(userId, request);
    return ResponseEntity.ok(
        ApiResponse.success(response, "Payment history retrieved successfully"));
  }

  @GetMapping("/{paymentId}")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(
      @AuthenticationPrincipal UUID userId, @PathVariable String paymentId) {
    PaymentStatusResponse response =
        paymentStatusService.getPaymentStatus(userId, parsePaymentId(paymentId));
    return ResponseEntity.ok(ApiResponse.success(response, "Payment retrieved successfully"));
  }

  /**
   * Binds the path segment as a string so a malformed id resolves to the same {@code 404} as an
   * unknown one, instead of failing type conversion and falling through to the generic {@code 500}
   * handler. Anything that parses still misses the ownership-scoped lookup and 404s there.
   */
  private static UUID parsePaymentId(String paymentId) {
    try {
      return UUID.fromString(paymentId);
    } catch (IllegalArgumentException ex) {
      throw new ResourceNotFoundException("Payment not found");
    }
  }
}
