package com.vegalife.controller.subscription;

import com.vegalife.dto.request.subscription.AcquirePlanRequest;
import com.vegalife.dto.request.subscription.SubscriptionHistoryRequest;
import com.vegalife.dto.response.subscription.AcquirePlanResponse;
import com.vegalife.dto.response.subscription.AvailablePlanResponse;
import com.vegalife.dto.response.subscription.RemainingUsageResponse;
import com.vegalife.dto.response.subscription.SubscriptionHistoryResponse;
import com.vegalife.enums.subscription.AcquireAction;
import com.vegalife.service.credit.PlanAcquisitionService;
import com.vegalife.service.credit.PlanCatalogService;
import com.vegalife.service.credit.RequestAllowanceService;
import com.vegalife.service.subscription.AcquisitionHistoryService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan catalogue, acquisition, and the member's remaining AI requests (issue #181, ADR-021).
 *
 * <p>{@code POST /api/subscriptions/acquire} is the single write entry point: it claims a free plan
 * or opens a checkout for a paid one, decided by the plan's price. There is no separate claim
 * endpoint and no cancel endpoint, because acquired allowance never expires.
 */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

  private final PlanCatalogService planCatalogService;
  private final PlanAcquisitionService planAcquisitionService;
  private final RequestAllowanceService requestAllowanceService;
  private final AcquisitionHistoryService acquisitionHistoryService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AvailablePlanResponse>>> getAvailablePlans() {
    List<AvailablePlanResponse> plans = planCatalogService.getAvailablePlans();
    return ResponseEntity.ok(ApiResponse.success(plans, "Plans retrieved successfully"));
  }

  /** The contract is deliberately one field: the member's remaining requests, nothing else. */
  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<RemainingUsageResponse>> getMyRemainingUsage(
      @AuthenticationPrincipal UUID userId) {

    RemainingUsageResponse response =
        RemainingUsageResponse.builder()
            .remainingUsage(requestAllowanceService.getRemainingUsage(userId))
            .build();
    return ResponseEntity.ok(
        ApiResponse.success(response, "Remaining usage retrieved successfully"));
  }

  /**
   * Claims a free plan or opens a checkout for a paid one — one route, two outcomes, chosen by the
   * plan's price. A repeated claim answers {@code 409}; a repeated purchase is a new acquisition.
   */
  @PostMapping("/acquire")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<AcquirePlanResponse>> acquirePlan(
      @AuthenticationPrincipal UUID userId,
      @Valid @RequestBody AcquirePlanRequest request,
      HttpServletRequest httpRequest) {

    AcquirePlanResponse response =
        planAcquisitionService.acquire(userId, request.getPlanCode(), clientIp(httpRequest));
    String message =
        response.getAction() == AcquireAction.CLAIMED
            ? "Plan claimed successfully"
            : "Payment session created";
    return ResponseEntity.ok(ApiResponse.success(response, message));
  }

  @GetMapping("/me/history")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<PageResponse<SubscriptionHistoryResponse>>> getMyHistory(
      @AuthenticationPrincipal UUID userId,
      @Valid @ParameterObject @ModelAttribute SubscriptionHistoryRequest request) {

    PageResponse<SubscriptionHistoryResponse> response =
        acquisitionHistoryService.getMyAcquisitions(userId, request);
    return ResponseEntity.ok(
        ApiResponse.success(response, "Acquisition history retrieved successfully"));
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
