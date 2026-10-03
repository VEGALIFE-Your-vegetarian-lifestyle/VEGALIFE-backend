package com.vegalife.controller.subscription;

import com.vegalife.dto.response.subscription.AvailablePlanResponse;
import com.vegalife.dto.response.subscription.SubscriptionMeResponse;
import com.vegalife.service.subscription.SubscriptionService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

  private final SubscriptionService subscriptionService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AvailablePlanResponse>>> getAvailablePlans() {
    List<AvailablePlanResponse> plans = subscriptionService.getAvailablePlans();
    return ResponseEntity.ok(ApiResponse.success(plans, "Plans retrieved successfully"));
  }

  @GetMapping("/me")
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<SubscriptionMeResponse>> getMySubscription(
      @AuthenticationPrincipal UUID userId) {

    SubscriptionMeResponse response = subscriptionService.getMySubscription(userId);
    return ResponseEntity.ok(ApiResponse.success(response, "Subscription retrieved successfully"));
  }
}
