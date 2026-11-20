package com.vegalife.unit.mapper.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.dto.mapper.recipe.RecipeMapper;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Whitebox coverage for the recipe mapper's dish projection: the recipe response carries the shared
 * dish's image rather than an image of its own. Runs the real mapper, no mocks.
 */
class RecipeMapperTest {

  private final RecipeMapper mapper = RecipeMapper.INSTANCE;

  @Test
  void toResponse_copiesTheDishImageOntoTheRecipe() {
    Recipe recipe = recipeFor(Dish.builder().imageUrl("https://cdn.example.com/pho.jpg").build());

    RecipeResponse response = mapper.toResponse(recipe, "pho", List.of());

    assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/pho.jpg");
  }

  @Test
  void toResponse_imageIsNullWhenTheDishHasNone() {
    Recipe recipe = recipeFor(Dish.builder().build());

    RecipeResponse response = mapper.toResponse(recipe, "pho", List.of());

    assertThat(response.getImageUrl()).isNull();
  }

  private Recipe recipeFor(Dish dish) {
    return Recipe.builder()
        .id(UUID.randomUUID())
        .dish(dish)
        .user(User.builder().id(UUID.randomUUID()).build())
        .name("Pho")
        .instructions("Cook it")
        .servings(2)
        .build();
  }
}
