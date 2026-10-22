package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.DashboardRequest;
import com.vegalife.dto.response.admin.DashboardResponse;
import com.vegalife.service.admin.AdminDashboardService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin dashboard aggregate metrics (issue #12). */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminDashboardController {

  private final AdminDashboardService adminDashboardService;

  @GetMapping
  public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
      @Valid @ModelAttribute DashboardRequest request) {
    DashboardResponse response = adminDashboardService.getDashboard(request);
    return ResponseEntity.ok(ApiResponse.success(response, "Dashboard retrieved successfully"));
  }
}
