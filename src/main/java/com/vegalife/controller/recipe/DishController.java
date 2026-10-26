package com.vegalife.controller.recipe;

import com.vegalife.dto.request.recipe.DishListRequest;
import com.vegalife.dto.request.recipe.DishSuggestionRequest;
import com.vegalife.dto.response.recipe.DishResponse;
import com.vegalife.dto.response.recipe.DishSuggestionResponse;
import com.vegalife.service.recipe.DishService;
import com.vegalife.service.recipe.DishSuggestionFacade;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dish browsing for the recipe form. Authenticated-only, matching {@code SecurityConfig}'s
 * catch-all rule: dishes are a member-facing catalogue, not public content.
 *
 * <p>The class-level {@link SecurityRequirement} keeps OpenAPI in step with that rule. {@code
 * OpenApiConfig} registers the {@code bearerAuth} scheme but does not apply it globally, so without
 * this annotation Swagger renders every operation lock-free and sends no token.
 */
@RestController
@RequestMapping("/api/dishes")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class DishController {

  private final DishService dishService;
  private final DishSuggestionFacade dishSuggestionFacade;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<DishResponse>>> listDishes(
      @Valid @ModelAttribute DishListRequest request) {
    PageResponse<DishResponse> dishes = dishService.listDishes(request);
    return ResponseEntity.ok(ApiResponse.success(dishes, "Dishes retrieved successfully"));
  }

  /** Only approved dishes resolve; see {@link DishService#getDish}. */
  @GetMapping("/{dishId}")
  public ResponseEntity<ApiResponse<DishResponse>> getDish(@PathVariable UUID dishId) {
    return ResponseEntity.ok(
        ApiResponse.success(dishService.getDish(dishId), "Dish retrieved successfully"));
  }

  /**
   * Dishes worth suggesting to the caller (issue #163), each with up to {@code recipesPerDish}
   * recommended recipes.
   *
   * <p>Unlike {@link #listDishes}, this is a suggestion surface: a dish appears only when at least
   * one of its approved recipes survives the caller's stored allergies and the supplied recipe
   * filters, so the list can be shorter than the catalogue and its totals reflect that. Allergies
   * are always taken from the caller's profile and never from a parameter.
   */
  @GetMapping("/suggestions")
  public ResponseEntity<ApiResponse<PageResponse<DishSuggestionResponse>>> suggestDishes(
      @AuthenticationPrincipal UUID userId, @Valid @ModelAttribute DishSuggestionRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            dishSuggestionFacade.suggest(userId, request),
            "Dish suggestions retrieved successfully"));
  }
}
