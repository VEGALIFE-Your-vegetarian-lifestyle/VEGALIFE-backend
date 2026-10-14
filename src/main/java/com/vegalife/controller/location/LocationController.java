package com.vegalife.controller.location;

import com.vegalife.dto.request.location.LocationListRequest;
import com.vegalife.dto.request.location.LocationNearbyRequest;
import com.vegalife.dto.response.location.LocationResponse;
import com.vegalife.service.location.LocationService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class LocationController {

  private final LocationService locationService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<LocationResponse>>> listLocations(
      @Valid @ModelAttribute LocationListRequest request) {
    PageResponse<LocationResponse> locations = locationService.listLocations(request);
    return ResponseEntity.ok(ApiResponse.success(locations, "Locations retrieved successfully"));
  }

  @GetMapping("/nearby")
  public ResponseEntity<ApiResponse<List<LocationResponse>>> listNearbyLocations(
      @Valid @ModelAttribute LocationNearbyRequest request) {
    List<LocationResponse> locations = locationService.listNearby(request);
    return ResponseEntity.ok(
        ApiResponse.success(locations, "Nearby locations retrieved successfully"));
  }
}
