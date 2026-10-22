package com.vegalife.controller.recipe;

import com.vegalife.dto.request.recipe.DishListRequest;
import com.vegalife.dto.response.recipe.DishResponse;
import com.vegalife.service.recipe.DishService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dish browsing for the recipe form. Authenticated-only, matching {@code SecurityConfig}'s
 * catch-all rule: dishes are a member-facing catalogue, not public content.
 */
@RestController
@RequestMapping("/api/dishes")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DishController {

  private final DishService dishService;

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
}
