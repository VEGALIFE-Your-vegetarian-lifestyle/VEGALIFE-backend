package com.vegalife.controller.admin;

import com.vegalife.dto.request.restaurant.RestaurantCreateRequest;
import com.vegalife.dto.request.restaurant.RestaurantUpdateRequest;
import com.vegalife.dto.response.admin.AdminRestaurantResponse;
import com.vegalife.service.restaurant.RestaurantService;
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
@RequestMapping("/api/admin/restaurants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
public class AdminRestaurantController {

  private final RestaurantService restaurantService;

  @PostMapping
  public ResponseEntity<ApiResponse<AdminRestaurantResponse>> createRestaurant(
      @AuthenticationPrincipal UUID adminId, @Valid @RequestBody RestaurantCreateRequest request) {
    AdminRestaurantResponse restaurant = restaurantService.createRestaurant(adminId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(restaurant, "Restaurant created successfully"));
  }

  @PatchMapping("/{restaurantId}")
  public ResponseEntity<ApiResponse<AdminRestaurantResponse>> updateRestaurant(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID restaurantId,
      @Valid @RequestBody RestaurantUpdateRequest request) {
    AdminRestaurantResponse restaurant =
        restaurantService.updateRestaurant(adminId, restaurantId, request);
    return ResponseEntity.ok(ApiResponse.success(restaurant, "Restaurant updated successfully"));
  }

  @DeleteMapping("/{restaurantId}")
  public ResponseEntity<ApiResponse<Void>> deleteRestaurant(
      @AuthenticationPrincipal UUID adminId, @PathVariable UUID restaurantId) {
    restaurantService.deleteRestaurant(adminId, restaurantId);
    return ResponseEntity.ok(ApiResponse.success(null, "Restaurant deleted successfully"));
  }
}
