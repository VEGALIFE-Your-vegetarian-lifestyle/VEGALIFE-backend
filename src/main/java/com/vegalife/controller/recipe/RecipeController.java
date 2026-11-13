package com.vegalife.controller.recipe;

import com.vegalife.dto.request.recipe.MyRecipeListRequest;
import com.vegalife.dto.request.recipe.RecipeCreateRequest;
import com.vegalife.dto.request.recipe.RecipeListRequest;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.service.recipe.RecipeService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
 *
 * <p>Reads split by audience: {@code GET /api/recipes} is the public approved catalogue and {@code
 * GET /api/recipes/{recipeId}} is public for an approved recipe but author-only otherwise
 * (BR-RECP-008). Only the caller's own list and creation require a token, so the class does not
 * carry a class-level {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

  private final RecipeService recipeService;

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<RecipeResponse>> createRecipe(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody RecipeCreateRequest request) {
    RecipeResponse recipe = recipeService.createRecipe(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(recipe, "Recipe created successfully"));
  }

  /** The caller's own recipes, newest first, paginated and filterable. */
  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<PageResponse<RecipeResponse>>> listMyRecipes(
      @AuthenticationPrincipal UUID userId,
      @Valid @ParameterObject @ModelAttribute MyRecipeListRequest request) {
    PageResponse<RecipeResponse> recipes = recipeService.listMyRecipes(userId, request);
    return ResponseEntity.ok(ApiResponse.success(recipes, "Recipes retrieved successfully"));
  }

  /** The public catalogue: approved, non-deleted recipes, newest first (BR-RECP-008). */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<RecipeResponse>>> listRecipes(
      @Valid @ParameterObject @ModelAttribute RecipeListRequest request) {
    PageResponse<RecipeResponse> recipes = recipeService.listApprovedRecipes(request);
    return ResponseEntity.ok(ApiResponse.success(recipes, "Recipes retrieved successfully"));
  }

  /** Public when the recipe is approved; otherwise only its author can read it (BR-RECP-008). */
  @GetMapping("/{recipeId}")
  public ResponseEntity<ApiResponse<RecipeResponse>> getRecipe(
      Authentication authentication, @PathVariable UUID recipeId) {
    UUID viewerId =
        authentication != null && authentication.getPrincipal() instanceof UUID id ? id : null;
    RecipeResponse recipe = recipeService.getRecipe(viewerId, recipeId);
    return ResponseEntity.ok(ApiResponse.success(recipe, "Recipe retrieved successfully"));
  }
}
