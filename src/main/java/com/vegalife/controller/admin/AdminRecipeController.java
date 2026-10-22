package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.AdminRecipeUpdateRequest;
import com.vegalife.dto.request.admin.ComponentModerationRequest;
import com.vegalife.dto.request.admin.RecipeListRequest;
import com.vegalife.dto.response.admin.AdminRecipeListResponse;
import com.vegalife.service.admin.AdminRecipeService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
 * Admin management of recipes (ADR-016).
 *
 * <p>There is no admin create: a recipe always originates from {@code POST /api/recipes}, so it
 * always has an author. An admin who needs one edits a member's recipe instead of inventing an
 * owner.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminRecipeController {

  private final AdminRecipeService adminRecipeService;

  @GetMapping("/recipes")
  public ResponseEntity<ApiResponse<PageResponse<AdminRecipeListResponse>>> listRecipes(
      @Valid @ModelAttribute RecipeListRequest request) {
    PageResponse<AdminRecipeListResponse> page = adminRecipeService.listRecipes(request);
    return ResponseEntity.ok(ApiResponse.success(page, "Recipes retrieved successfully"));
  }

  @GetMapping("/recipes/{recipeId}")
  public ResponseEntity<ApiResponse<AdminRecipeListResponse>> getRecipe(
      @PathVariable UUID recipeId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            adminRecipeService.getRecipe(recipeId), "Recipe retrieved successfully"));
  }

  @PatchMapping("/recipes/{recipeId}")
  public ResponseEntity<ApiResponse<AdminRecipeListResponse>> updateRecipe(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID recipeId,
      @Valid @RequestBody AdminRecipeUpdateRequest request) {
    AdminRecipeListResponse recipe = adminRecipeService.updateRecipe(adminId, recipeId, request);
    return ResponseEntity.ok(ApiResponse.success(recipe, "Recipe updated successfully"));
  }

  @DeleteMapping("/recipes/{recipeId}")
  public ResponseEntity<ApiResponse<Void>> deleteRecipe(
      @AuthenticationPrincipal UUID adminId, @PathVariable UUID recipeId) {
    adminRecipeService.deleteRecipe(adminId, recipeId);
    return ResponseEntity.ok(ApiResponse.success(null, "Recipe deleted successfully"));
  }

  @PostMapping("/recipes/{recipeId}/approve")
  public ResponseEntity<ApiResponse<AdminRecipeListResponse>> approveRecipe(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID recipeId,
      @Valid @RequestBody(required = false) ComponentModerationRequest request) {
    AdminRecipeListResponse recipe =
        adminRecipeService.approveRecipe(adminId, recipeId, reasonOf(request));
    return ResponseEntity.ok(ApiResponse.success(recipe, "Recipe approved successfully"));
  }

  @PostMapping("/recipes/{recipeId}/reject")
  public ResponseEntity<ApiResponse<AdminRecipeListResponse>> rejectRecipe(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID recipeId,
      @Valid @RequestBody(required = false) ComponentModerationRequest request) {
    AdminRecipeListResponse recipe =
        adminRecipeService.rejectRecipe(adminId, recipeId, reasonOf(request));
    return ResponseEntity.ok(ApiResponse.success(recipe, "Recipe rejected successfully"));
  }

  /** The reason is optional, so an absent body must not be a 400. */
  private static String reasonOf(ComponentModerationRequest request) {
    return request == null ? null : request.getReason();
  }
}
