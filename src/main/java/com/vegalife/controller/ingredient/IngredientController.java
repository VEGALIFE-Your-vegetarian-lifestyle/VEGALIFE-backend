package com.vegalife.controller.ingredient;

import com.vegalife.dto.request.ingredient.IngredientListRequest;
import com.vegalife.dto.response.ingredient.IngredientResponse;
import com.vegalife.service.ingredient.IngredientService;
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
@RequestMapping("/api/ingredients")
@RequiredArgsConstructor
public class IngredientController {

  private final IngredientService ingredientService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<IngredientResponse>>> listIngredients(
      @Valid @ModelAttribute IngredientListRequest request) {
    PageResponse<IngredientResponse> ingredients = ingredientService.listIngredients(request);
    return ResponseEntity.ok(
        ApiResponse.success(ingredients, "Ingredients retrieved successfully"));
  }
}
