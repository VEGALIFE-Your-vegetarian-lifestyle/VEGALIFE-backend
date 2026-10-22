package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.AdminPlanCreateRequest;
import com.vegalife.dto.request.admin.AdminPlanUpdateRequest;
import com.vegalife.dto.response.admin.AdminPlanResponse;
import com.vegalife.service.subscription.AdminPlanService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin CRUD for AI subscription plans (issue #119). */
@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminPlanController {

  private final AdminPlanService adminPlanService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AdminPlanResponse>>> listPlans() {
    List<AdminPlanResponse> plans = adminPlanService.listPlans();
    return ResponseEntity.ok(ApiResponse.success(plans, "Plans retrieved successfully"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AdminPlanResponse>> createPlan(
      @Valid @RequestBody AdminPlanCreateRequest request) {
    AdminPlanResponse plan = adminPlanService.createPlan(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(plan, "Plan created successfully"));
  }

  @PatchMapping("/{planId}")
  public ResponseEntity<ApiResponse<AdminPlanResponse>> updatePlan(
      @PathVariable UUID planId, @Valid @RequestBody AdminPlanUpdateRequest request) {
    AdminPlanResponse plan = adminPlanService.updatePlan(planId, request);
    return ResponseEntity.ok(ApiResponse.success(plan, "Plan updated successfully"));
  }

  @DeleteMapping("/{planId}")
  public ResponseEntity<ApiResponse<Void>> deactivatePlan(@PathVariable UUID planId) {
    adminPlanService.deactivatePlan(planId);
    return ResponseEntity.ok(ApiResponse.success(null, "Plan deactivated successfully"));
  }
}
