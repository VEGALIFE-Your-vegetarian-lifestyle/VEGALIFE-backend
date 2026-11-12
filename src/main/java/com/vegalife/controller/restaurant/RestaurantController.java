package com.vegalife.controller.restaurant;

import com.vegalife.dto.request.restaurant.RestaurantListRequest;
import com.vegalife.dto.request.restaurant.RestaurantNearbyRequest;
import com.vegalife.dto.response.restaurant.RestaurantResponse;
import com.vegalife.service.restaurant.RestaurantService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
public class RestaurantController {

  private final RestaurantService restaurantService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<RestaurantResponse>>> listRestaurants(
      @Valid @ParameterObject @ModelAttribute RestaurantListRequest request) {
    PageResponse<RestaurantResponse> restaurants = restaurantService.listRestaurants(request);
    return ResponseEntity.ok(
        ApiResponse.success(restaurants, "Restaurants retrieved successfully"));
  }

  @GetMapping("/nearby")
  public ResponseEntity<ApiResponse<List<RestaurantResponse>>> listNearbyRestaurants(
      @Valid @ParameterObject @ModelAttribute RestaurantNearbyRequest request) {
    List<RestaurantResponse> restaurants = restaurantService.listNearby(request);
    return ResponseEntity.ok(
        ApiResponse.success(restaurants, "Nearby restaurants retrieved successfully"));
  }

  @GetMapping("/{restaurantId}")
  public ResponseEntity<ApiResponse<RestaurantResponse>> getRestaurant(
      @PathVariable UUID restaurantId) {
    RestaurantResponse restaurant = restaurantService.getRestaurant(restaurantId);
    return ResponseEntity.ok(ApiResponse.success(restaurant, "Restaurant retrieved successfully"));
  }
}
