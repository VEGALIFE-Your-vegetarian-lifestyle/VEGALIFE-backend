package com.vegalife.controller.recipe;

import com.vegalife.dto.request.recipe.DishListRequest;
import com.vegalife.dto.response.recipe.DishResponse;
import com.vegalife.service.recipe.DishService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dishes")
@RequiredArgsConstructor
public class DishController {

  private final DishService dishService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<DishResponse>>> listDishes(
      @Valid @ModelAttribute DishListRequest request) {
    PageResponse<DishResponse> dishes = dishService.listDishes(request);
    return ResponseEntity.ok(ApiResponse.success(dishes, "Dishes retrieved successfully"));
  }
}
