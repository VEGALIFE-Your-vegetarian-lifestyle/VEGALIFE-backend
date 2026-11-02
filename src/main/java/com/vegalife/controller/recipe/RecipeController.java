package com.vegalife.controller.recipe;

import com.vegalife.dto.request.recipe.RecipeCreateRequest;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.service.recipe.RecipeService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Member-facing recipe endpoints.
 *
 * <p>Writes are limited to {@code POST} on purpose (BR-RECP-001): a member who could edit a recipe
 * after its post passed the filter could turn clean text into something else without being reviewed
 * again. Corrections go through an admin.
 */
@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
public class RecipeController {

  private final RecipeService recipeService;

  @PostMapping
  public ResponseEntity<ApiResponse<RecipeResponse>> createRecipe(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody RecipeCreateRequest request) {
    RecipeResponse recipe = recipeService.createRecipe(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(recipe, "Recipe created successfully"));
  }

  /** The caller's own recipes, newest first. */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<List<RecipeResponse>>> listMyRecipes(
      @AuthenticationPrincipal UUID userId) {
    List<RecipeResponse> recipes = recipeService.listMyRecipes(userId);
    return ResponseEntity.ok(ApiResponse.success(recipes, "Recipes retrieved successfully"));
  }

  @GetMapping("/{recipeId}")
  public ResponseEntity<ApiResponse<RecipeResponse>> getRecipe(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID recipeId) {
    RecipeResponse recipe = recipeService.getRecipe(userId, recipeId);
    return ResponseEntity.ok(ApiResponse.success(recipe, "Recipe retrieved successfully"));
  }
}
