package com.vegalife.unit.mapper.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.dto.mapper.recipe.RecipeSummaryMapper;
import com.vegalife.dto.response.menu.RecipeSummaryResponse;
import com.vegalife.enums.recipe.Difficulty;
import com.vegalife.model.recipe.Recipe;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Whitebox coverage for the recommendation shape: it takes the image as a parameter because the
 * image belongs to the shared dish the caller already holds, not to the recipe row itself. Runs the
 * real mapper, no mocks.
 */
class RecipeSummaryMapperTest {

  @Test
  void toSummary_carriesTheSuppliedDishImage() {
    Recipe recipe = recipe();

    RecipeSummaryResponse summary =
        RecipeSummaryMapper.toSummary(recipe, "https://cdn.example.com/pho.jpg");

    assertThat(summary.getImageUrl()).isEqualTo("https://cdn.example.com/pho.jpg");
    assertThat(summary.getId()).isEqualTo(recipe.getId());
  }

  @Test
  void toSummary_leavesTheImageNullWhenTheDishHasNone() {
    assertThat(RecipeSummaryMapper.toSummary(recipe(), null).getImageUrl()).isNull();
  }

  private Recipe recipe() {
    return Recipe.builder()
        .id(UUID.randomUUID())
        .name("Pho")
        .difficulty(Difficulty.EASY)
        .servings(2)
        .build();
  }
}
