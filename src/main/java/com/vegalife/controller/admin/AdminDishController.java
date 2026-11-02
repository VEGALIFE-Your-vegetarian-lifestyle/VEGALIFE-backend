package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.AdminComponentListRequest;
import com.vegalife.dto.request.admin.AdminDishCreateRequest;
import com.vegalife.dto.request.admin.AdminDishUpdateRequest;
import com.vegalife.dto.request.admin.ComponentModerationRequest;
import com.vegalife.dto.response.admin.AdminDishResponse;
import com.vegalife.service.admin.AdminDishService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin management of dish names (ADR-016).
 *
 * <p>The list includes pending and rejected names, unlike {@code GET /api/dishes}, because the
 * review queue is the reason this endpoint exists.
 *
 * <p>{@code PATCH} rather than {@code PUT}: the update body is partial, and calling a partial
 * update {@code PUT} would tell clients they can omit fields and replace the rest.
 */
@RestController
@RequestMapping("/api/admin/dishes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
public class AdminDishController {

  private final AdminDishService adminDishService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<AdminDishResponse>>> listDishes(
      @Valid @ModelAttribute AdminComponentListRequest request) {
    PageResponse<AdminDishResponse> page = adminDishService.listDishes(request);
    return ResponseEntity.ok(ApiResponse.success(page, "Dishes retrieved successfully"));
  }

  @GetMapping("/{dishId}")
  public ResponseEntity<ApiResponse<AdminDishResponse>> getDish(@PathVariable UUID dishId) {
    return ResponseEntity.ok(
        ApiResponse.success(adminDishService.getDish(dishId), "Dish retrieved successfully"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AdminDishResponse>> createDish(
      @AuthenticationPrincipal UUID adminId, @Valid @RequestBody AdminDishCreateRequest request) {
    AdminDishResponse dish = adminDishService.createDish(adminId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(dish, "Dish created successfully"));
  }

  @PatchMapping("/{dishId}")
  public ResponseEntity<ApiResponse<AdminDishResponse>> updateDish(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID dishId,
      @Valid @RequestBody AdminDishUpdateRequest request) {
    AdminDishResponse dish = adminDishService.updateDish(adminId, dishId, request);
    return ResponseEntity.ok(ApiResponse.success(dish, "Dish updated successfully"));
  }

  @DeleteMapping("/{dishId}")
  public ResponseEntity<ApiResponse<Void>> deleteDish(
      @AuthenticationPrincipal UUID adminId, @PathVariable UUID dishId) {
    adminDishService.deleteDish(adminId, dishId);
    return ResponseEntity.ok(ApiResponse.success(null, "Dish deleted successfully"));
  }

  @PostMapping("/{dishId}/approve")
  public ResponseEntity<ApiResponse<AdminDishResponse>> approveDish(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID dishId,
      @Valid @RequestBody(required = false) ComponentModerationRequest request) {
    AdminDishResponse dish = adminDishService.approveDish(adminId, dishId, reasonOf(request));
    return ResponseEntity.ok(ApiResponse.success(dish, "Dish approved successfully"));
  }

  @PostMapping("/{dishId}/reject")
  public ResponseEntity<ApiResponse<AdminDishResponse>> rejectDish(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID dishId,
      @Valid @RequestBody(required = false) ComponentModerationRequest request) {
    AdminDishResponse dish = adminDishService.rejectDish(adminId, dishId, reasonOf(request));
    return ResponseEntity.ok(ApiResponse.success(dish, "Dish rejected successfully"));
  }

  /** The reason is optional, so an absent body must not be a 400. */
  private static String reasonOf(ComponentModerationRequest request) {
    return request == null ? null : request.getReason();
  }
}
