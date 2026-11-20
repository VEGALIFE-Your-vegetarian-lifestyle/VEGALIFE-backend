package com.vegalife.unit.mapper.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.dto.mapper.admin.AdminRecipeMapper;
import com.vegalife.dto.response.admin.AdminRecipeListResponse;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * Whitebox coverage for the admin recipe projection: it reports the shared dish's image so an admin
 * can see which dishes still need artwork. Runs the real mapper, no mocks.
 */
class AdminRecipeMapperTest {

  private final AdminRecipeMapper mapper = Mappers.getMapper(AdminRecipeMapper.class);

  @Test
  void toResponse_copiesTheDishImage() {
    AdminRecipeListResponse response =
        mapper.toResponse(
            recipeFor(Dish.builder().imageUrl("https://cdn.example.com/pho.jpg").build()));

    assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/pho.jpg");
  }

  @Test
  void toResponse_imageIsNullWhenTheDishHasNone() {
    assertThat(mapper.toResponse(recipeFor(Dish.builder().build())).getImageUrl()).isNull();
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
