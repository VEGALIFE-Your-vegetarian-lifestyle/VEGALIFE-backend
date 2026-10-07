package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.AdminPaymentListRequest;
import com.vegalife.dto.response.payment.PaymentHistoryItemResponse;
import com.vegalife.service.payment.PaymentHistoryService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin payment history (FR-004, FR-005). Authorization is path-based: {@code /api/admin/**} is
 * already restricted to {@code ROLE_ADMIN} in {@code SecurityConfig} — no endpoint-level annotation
 * is added here, and none is removed from elsewhere (NFR-SEC-001).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminPaymentController {

  private final PaymentHistoryService paymentHistoryService;

  @GetMapping("/payments")
  public ResponseEntity<ApiResponse<PageResponse<PaymentHistoryItemResponse>>> listPayments(
      @Valid @ModelAttribute AdminPaymentListRequest request) {
    PageResponse<PaymentHistoryItemResponse> page = paymentHistoryService.listPayments(request);
    return ResponseEntity.ok(ApiResponse.success(page, "Payment history retrieved successfully"));
  }
}
