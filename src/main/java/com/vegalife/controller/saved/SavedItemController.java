package com.vegalife.controller.saved;

import com.vegalife.dto.request.saved.SavedListRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.service.saved.SavedItemService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's saved posts and saved recipes (BR-SAVED-001).
 *
 * <p>Two endpoint pairs rather than one polymorphic surface: a save is either of a post or of a
 * recipe, and the URL says which, so the client never infers a target type from a body field.
 *
 * <p>Every handler is authenticated and there is no id in the path that identifies the member — the
 * caller is the saved list's only reader. That is deliberate: there is no "someone else's saved
 * items" surface to get the authorization wrong on.
 */
@RestController
@RequestMapping("/api/saved")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class SavedItemController {

  private final SavedItemService savedItemService;

  @PostMapping("/posts/{postId}")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<Void>> savePost(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID postId) {
    savedItemService.savePost(userId, postId);
    return ResponseEntity.ok(ApiResponse.success(null, "Post saved successfully"));
  }

  @DeleteMapping("/posts/{postId}")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<Void>> unsavePost(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID postId) {
    savedItemService.unsavePost(userId, postId);
    return ResponseEntity.ok(ApiResponse.success(null, "Post removed from saved items"));
  }

  @GetMapping("/posts")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<PageResponse<PostListResponse>>> listSavedPosts(
      @AuthenticationPrincipal UUID userId,
      @Valid @ParameterObject @ModelAttribute SavedListRequest request) {
    PageResponse<PostListResponse> posts = savedItemService.listSavedPosts(userId, request);
    return ResponseEntity.ok(ApiResponse.success(posts, "Saved posts retrieved successfully"));
  }

  @PostMapping("/recipes/{recipeId}")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<Void>> saveRecipe(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID recipeId) {
    savedItemService.saveRecipe(userId, recipeId);
    return ResponseEntity.ok(ApiResponse.success(null, "Recipe saved successfully"));
  }

  @DeleteMapping("/recipes/{recipeId}")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<Void>> unsaveRecipe(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID recipeId) {
    savedItemService.unsaveRecipe(userId, recipeId);
    return ResponseEntity.ok(ApiResponse.success(null, "Recipe removed from saved items"));
  }

  @GetMapping("/recipes")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<PageResponse<RecipeResponse>>> listSavedRecipes(
      @AuthenticationPrincipal UUID userId,
      @Valid @ParameterObject @ModelAttribute SavedListRequest request) {
    PageResponse<RecipeResponse> recipes = savedItemService.listSavedRecipes(userId, request);
    return ResponseEntity.ok(ApiResponse.success(recipes, "Saved recipes retrieved successfully"));
  }
}
