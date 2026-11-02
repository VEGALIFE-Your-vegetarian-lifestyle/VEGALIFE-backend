package com.vegalife.service.recipe;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * Optional recipe-level filters for dish suggestions (issue #163). Every field is nullable; a null
 * means "no constraint".
 *
 * <p>These narrow <em>recipes</em>, not dishes. The same object decides both whether a dish is
 * eligible (it needs at least one surviving recipe) and which recipes are recommended for it, so a
 * caller can never see a dish whose only matching recipe was filtered out of the recommendation
 * list.
 *
 * <p>Nutrition filters are strict: a recipe whose value is unknown is excluded, never treated as
 * passing. Unknown is not the same as zero.
 */
@Data
@Builder
public class RecipeFilter {

  /** {@code EASY}, {@code MEDIUM} or {@code HARD}; null for any difficulty. */
  private String difficulty;

  private Integer maxPrepTimeMinutes;
  private Integer maxCookTimeMinutes;

  /** Upper bound on {@code prep + cook}, treating a missing component as 0. */
  private Integer maxTotalTimeMinutes;

  private BigDecimal minCalories;
  private BigDecimal maxCalories;
  private BigDecimal minProteinG;

  /** True when no constraint is set, letting callers skip filtering entirely. */
  public boolean isEmpty() {
    return difficulty == null
        && maxPrepTimeMinutes == null
        && maxCookTimeMinutes == null
        && maxTotalTimeMinutes == null
        && minCalories == null
        && maxCalories == null
        && minProteinG == null;
  }
}
