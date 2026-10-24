package com.vegalife.service.recipe;

import com.vegalife.dto.response.menu.RecipeSummaryResponse;
import com.vegalife.model.recipe.Recipe;

/**
 * The single place a {@link Recipe} becomes the recommendation shape (issue #163). Used by both the
 * meal-plan response and the dish suggestions, so the two can never describe a recipe differently.
 */
public final class RecipeSummaryMapper {

  private RecipeSummaryMapper() {}

  public static RecipeSummaryResponse toSummary(Recipe recipe) {
    return RecipeSummaryResponse.builder()
        .id(recipe.getId())
        .name(recipe.getName())
        .description(recipe.getDescription())
        .prepTimeMinutes(recipe.getPrepTimeMinutes())
        .cookTimeMinutes(recipe.getCookTimeMinutes())
        .servings(recipe.getServings())
        .difficulty(recipe.getDifficulty() == null ? null : recipe.getDifficulty().name())
        .calories(recipe.getCalories())
        .proteinG(recipe.getProteinG())
        .carbohydrateG(recipe.getCarbohydrateG())
        .fatG(recipe.getFatG())
        .fiberG(recipe.getFiberG())
        .build();
  }
}
