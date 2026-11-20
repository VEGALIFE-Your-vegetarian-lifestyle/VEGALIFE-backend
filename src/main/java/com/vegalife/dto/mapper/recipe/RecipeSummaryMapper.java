package com.vegalife.dto.mapper.recipe;

import com.vegalife.dto.response.menu.RecipeSummaryResponse;
import com.vegalife.model.recipe.Recipe;

/**
 * The single place a {@link Recipe} becomes the recommendation shape (issue #163). Used by both the
 * meal-plan response and the dish suggestions, so the two can never describe a recipe differently.
 */
public final class RecipeSummaryMapper {

  private RecipeSummaryMapper() {}

  /**
   * Builds the recommendation shape for one recipe.
   *
   * <p>{@code imageUrl} is passed in rather than read from {@code recipe.getDish()}: the image is
   * the shared dish's, both callers already hold that dish, and reading through the lazy
   * association here would cost one query per recommendation for a value the caller has in hand.
   */
  public static RecipeSummaryResponse toSummary(Recipe recipe, String imageUrl) {
    return RecipeSummaryResponse.builder()
        .id(recipe.getId())
        .name(recipe.getName())
        .description(recipe.getDescription())
        .imageUrl(imageUrl)
        .prepTimeMinutes(recipe.getPrepTimeMinutes())
        .cookTimeMinutes(recipe.getCookTimeMinutes())
        .servings(recipe.getServings())
        .difficulty(recipe.getDifficulty() == null ? null : recipe.getDifficulty().name())
        .calories(recipe.getCalories())
        .proteinG(recipe.getProteinG())
        .carbohydrateG(recipe.getCarbohydrateG())
        .fatG(recipe.getFatG())
        .fiberG(recipe.getFiberG())
        .nutritionResolved(recipe.getCalories() != null)
        .build();
  }
}
