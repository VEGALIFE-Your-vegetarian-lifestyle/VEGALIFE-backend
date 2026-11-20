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
 * passing. Unknown is not the same as zero. Since ADR-022 a recipe's nutrition is resolved only
 * when every ingredient line is, so a null here genuinely means "the catalogue cannot price this
 * dish yet" rather than "the author left it blank".
 *
 * <p>All five macros carry both bounds. A caller building a meal plan asks for "high protein, low
 * fat, at least 8 g of fibre" as readily as it asks for a calorie window, and splitting that across
 * two code paths would let one of them drift.
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
  private BigDecimal maxProteinG;

  private BigDecimal minCarbohydrateG;
  private BigDecimal maxCarbohydrateG;

  private BigDecimal minFatG;
  private BigDecimal maxFatG;

  private BigDecimal minFiberG;
  private BigDecimal maxFiberG;

  /** True when no constraint is set, letting callers skip filtering entirely. */
  public boolean isEmpty() {
    return difficulty == null
        && maxPrepTimeMinutes == null
        && maxCookTimeMinutes == null
        && maxTotalTimeMinutes == null
        && minCalories == null
        && maxCalories == null
        && minProteinG == null
        && maxProteinG == null
        && minCarbohydrateG == null
        && maxCarbohydrateG == null
        && minFatG == null
        && maxFatG == null
        && minFiberG == null
        && maxFiberG == null;
  }
}
