package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.AdminComponentListRequest;
import com.vegalife.dto.request.admin.AdminIngredientCreateRequest;
import com.vegalife.dto.request.admin.AdminIngredientUpdateRequest;
import com.vegalife.dto.request.admin.ComponentModerationRequest;
import com.vegalife.dto.response.admin.AdminIngredientResponse;
import com.vegalife.service.admin.AdminIngredientService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
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

/** Admin management of ingredient names (ADR-016); mirrors {@link AdminDishController}. */
@RestController
@RequestMapping("/api/admin/ingredients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminIngredientController {

  private final AdminIngredientService adminIngredientService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<AdminIngredientResponse>>> listIngredients(
      @Valid @ModelAttribute AdminComponentListRequest request) {
    PageResponse<AdminIngredientResponse> page = adminIngredientService.listIngredients(request);
    return ResponseEntity.ok(ApiResponse.success(page, "Ingredients retrieved successfully"));
  }

  @GetMapping("/{ingredientId}")
  public ResponseEntity<ApiResponse<AdminIngredientResponse>> getIngredient(
      @PathVariable UUID ingredientId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            adminIngredientService.getIngredient(ingredientId),
            "Ingredient retrieved successfully"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AdminIngredientResponse>> createIngredient(
      @AuthenticationPrincipal UUID adminId,
      @Valid @RequestBody AdminIngredientCreateRequest request) {
    AdminIngredientResponse ingredient = adminIngredientService.createIngredient(adminId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(ingredient, "Ingredient created successfully"));
  }

  @PatchMapping("/{ingredientId}")
  public ResponseEntity<ApiResponse<AdminIngredientResponse>> updateIngredient(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID ingredientId,
      @Valid @RequestBody AdminIngredientUpdateRequest request) {
    AdminIngredientResponse ingredient =
        adminIngredientService.updateIngredient(adminId, ingredientId, request);
    return ResponseEntity.ok(ApiResponse.success(ingredient, "Ingredient updated successfully"));
  }

  @DeleteMapping("/{ingredientId}")
  public ResponseEntity<ApiResponse<Void>> deleteIngredient(
      @AuthenticationPrincipal UUID adminId, @PathVariable UUID ingredientId) {
    adminIngredientService.deleteIngredient(adminId, ingredientId);
    return ResponseEntity.ok(ApiResponse.success(null, "Ingredient deleted successfully"));
  }

  @PostMapping("/{ingredientId}/approve")
  public ResponseEntity<ApiResponse<AdminIngredientResponse>> approveIngredient(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID ingredientId,
      @Valid @RequestBody(required = false) ComponentModerationRequest request) {
    AdminIngredientResponse ingredient =
        adminIngredientService.approveIngredient(adminId, ingredientId, reasonOf(request));
    return ResponseEntity.ok(ApiResponse.success(ingredient, "Ingredient approved successfully"));
  }

  @PostMapping("/{ingredientId}/reject")
  public ResponseEntity<ApiResponse<AdminIngredientResponse>> rejectIngredient(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID ingredientId,
      @Valid @RequestBody(required = false) ComponentModerationRequest request) {
    AdminIngredientResponse ingredient =
        adminIngredientService.rejectIngredient(adminId, ingredientId, reasonOf(request));
    return ResponseEntity.ok(ApiResponse.success(ingredient, "Ingredient rejected successfully"));
  }

  /** The reason is optional, so an absent body must not be a 400. */
  private static String reasonOf(ComponentModerationRequest request) {
    return request == null ? null : request.getReason();
  }
}
