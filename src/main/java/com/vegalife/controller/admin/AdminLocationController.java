package com.vegalife.controller.admin;

import com.vegalife.dto.request.location.LocationCreateRequest;
import com.vegalife.dto.request.location.LocationUpdateRequest;
import com.vegalife.dto.response.admin.AdminLocationResponse;
import com.vegalife.service.location.LocationService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/locations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
public class AdminLocationController {

  private final LocationService locationService;

  @PostMapping
  public ResponseEntity<ApiResponse<AdminLocationResponse>> createLocation(
      @AuthenticationPrincipal UUID adminId, @Valid @RequestBody LocationCreateRequest request) {
    AdminLocationResponse location = locationService.createLocation(adminId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(location, "Location created successfully"));
  }

  @PatchMapping("/{locationId}")
  public ResponseEntity<ApiResponse<AdminLocationResponse>> updateLocation(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID locationId,
      @Valid @RequestBody LocationUpdateRequest request) {
    AdminLocationResponse location = locationService.updateLocation(adminId, locationId, request);
    return ResponseEntity.ok(ApiResponse.success(location, "Location updated successfully"));
  }

  @DeleteMapping("/{locationId}")
  public ResponseEntity<ApiResponse<Void>> deleteLocation(
      @AuthenticationPrincipal UUID adminId, @PathVariable UUID locationId) {
    locationService.deleteLocation(adminId, locationId);
    return ResponseEntity.ok(ApiResponse.success(null, "Location deleted successfully"));
  }
}
