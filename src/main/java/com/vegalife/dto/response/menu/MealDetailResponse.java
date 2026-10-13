package com.vegalife.dto.response.menu;

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
}
