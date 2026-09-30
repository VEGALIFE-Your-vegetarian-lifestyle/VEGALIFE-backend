package com.vegalife.controller.recipe;

import com.vegalife.dto.request.recipe.RecipeCreateRequest;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.service.recipe.RecipeService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class RecipeController {

  private final RecipeService recipeService;

  @PostMapping
  public ResponseEntity<ApiResponse<RecipeResponse>> createRecipe(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody RecipeCreateRequest request) {
    RecipeResponse recipe = recipeService.createRecipe(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(recipe, "Recipe created successfully"));
  }
}
