package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.DashboardRequest;
import com.vegalife.dto.response.admin.DashboardChartsResponse;
import com.vegalife.dto.response.admin.DashboardResponse;
import com.vegalife.service.admin.AdminDashboardService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin dashboard aggregates.
 *
 * <p>Two endpoints on one path, split by whether the data moves over time: {@code GET
 * /api/admin/dashboard} is a lifetime / current-state snapshot and takes no parameters, while
 * {@code GET /api/admin/dashboard/charts} is the only time-filtered surface. They are cached
 * separately and with different TTLs.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
public class AdminDashboardController {

  private final AdminDashboardService adminDashboardService;

  /** Lifetime and current-state platform metrics. No time filter, so no query parameters. */
  @GetMapping
  public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
    DashboardResponse response = adminDashboardService.getSummary();
    return ResponseEntity.ok(ApiResponse.success(response, "Dashboard retrieved successfully"));
  }

  /** Bucketed time series for the selected range. */
  @GetMapping("/charts")
  public ResponseEntity<ApiResponse<DashboardChartsResponse>> getCharts(
      @Valid @ParameterObject @ModelAttribute DashboardRequest request) {
    DashboardChartsResponse response = adminDashboardService.getCharts(request);
    return ResponseEntity.ok(
        ApiResponse.success(response, "Dashboard charts retrieved successfully"));
  }
}
