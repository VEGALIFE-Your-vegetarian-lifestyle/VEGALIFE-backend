package com.vegalife.dto.response.menu;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One meal slot of a meal-plan day. Unlike {@link MealResponse} — which the write paths return and
 * which identifies the dish by id and name only — this embeds the full {@link DishResponse}, since
 * a meal-plan reader is expected to describe the food rather than look it up.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealDetailResponse {

  /** {@code BREAKFAST}, {@code LUNCH} or {@code DINNER}. */
  private String mealType;

  private Integer servings;

  private DishResponse dish;

  /**
   * Up to three recipes worth recommending for {@link #dish} (issue #163), best first. Empty when
   * none qualifies — every approved recipe of this dish is missing, deleted, or matches one of the
   * caller's stored allergies — so a meal is never dropped just because it has no recommendation.
   */
  private List<RecipeSummaryResponse> recommendedRecipes;
}
