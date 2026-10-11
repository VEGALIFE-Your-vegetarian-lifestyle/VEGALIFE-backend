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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin payment history (FR-004, FR-005). Restricted to {@code ROLE_ADMIN} by the class-level
 * {@code @PreAuthorize}; the {@code /api/admin/**} path rule in {@code SecurityConfig} is retained
 * as defence in depth (NFR-SEC-001).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
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
