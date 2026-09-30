package com.vegalife.unit.controller.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.recipe.RecipeController;
import com.vegalife.dto.request.recipe.RecipeCreateRequest;
import com.vegalife.dto.request.recipe.RecipeIngredientItem;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.service.recipe.RecipeService;
import com.vegalife.shared.dto.ApiResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class RecipeControllerTest {

  @Mock private RecipeService recipeService;

  @InjectMocks private RecipeController recipeController;

  @Test
  void createRecipeReturns201WithMappedBody() {
    UUID userId = UUID.randomUUID();
    RecipeCreateRequest request =
        RecipeCreateRequest.builder()
            .name("Rainbow Bowl")
            .dishName("tofu stir-fry")
            .instructions("Chop and cook")
            .servings(2)
            .difficulty("EASY")
            .ingredients(
                List.of(
                    RecipeIngredientItem.builder()
                        .name("tofu")
                        .amount(new BigDecimal("150"))
                        .unit("g")
                        .build()))
            .build();
    RecipeResponse recipe =
        RecipeResponse.builder().id(UUID.randomUUID()).name("Rainbow Bowl").build();
    when(recipeService.createRecipe(userId, request)).thenReturn(recipe);

    ResponseEntity<ApiResponse<RecipeResponse>> result =
        recipeController.createRecipe(userId, request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody().isSuccess()).isTrue();
    assertThat(result.getBody().getMessage()).isEqualTo("Recipe created successfully");
    assertThat(result.getBody().getData()).isSameAs(recipe);
    verify(recipeService).createRecipe(userId, request);
  }
}
