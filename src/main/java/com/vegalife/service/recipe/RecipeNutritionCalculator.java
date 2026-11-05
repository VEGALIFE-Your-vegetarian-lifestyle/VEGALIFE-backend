package com.vegalife.service.recipe;

import com.vegalife.model.recipe.Ingredient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Turns a recipe's ingredient lines into whole-recipe nutrition totals (ADR-022).
 *
 * <p>The arithmetic is a single multiply per line because every ingredient stores nutrition per one
 * unit and every line's amount is already in that same unit:
 *
 * <pre>
 *   total = Σ (amount_i * value_i)
 * </pre>
 *
 * <p>Deliberately free of Spring, transactions and persistence so the rule can be tested directly.
 *
 * <p><strong>Completeness is all-or-nothing.</strong> If any line's ingredient has an unresolved
 * unit or missing nutrition, the result is {@code null} rather than the sum of the lines that did
 * resolve. A partial sum would be a plausible, authoritative-looking number that silently omits an
 * ingredient - the same failure mode as treating unknown as zero, which this codebase already
 * rejects for {@code recipe.score} (ADR-016).
 */
public final class RecipeNutritionCalculator {

  /** Totals are stored at scale 2; rounding happens once, at the end, never per line. */
  private static final int OUTPUT_SCALE = 2;

  private RecipeNutritionCalculator() {}

  /**
   * The nutrition of one ingredient line.
   *
   * <p>Not rounded: rounding happens once over the summed totals, so three lines of {@code 0.005}
   * kcal contribute 0.015 and become 0.02, rather than rounding up individually to 0.03.
   *
   * @param amount the quantity in the ingredient's own unit
   * @param ingredient the resolved ingredient, unit and nutrition included
   * @return per-line nutrition, or {@code null} when the ingredient is incomplete
   */
  public static LineNutrition lineNutrition(BigDecimal amount, Ingredient ingredient) {
    if (amount == null || ingredient == null || !ingredient.hasResolvedNutrition()) {
      return null;
    }
    return new LineNutrition(
        amount.multiply(ingredient.getCalories()),
        amount.multiply(ingredient.getProteinG()),
        amount.multiply(ingredient.getCarbohydrateG()),
        amount.multiply(ingredient.getFatG()),
        amount.multiply(ingredient.getFiberG()));
  }

  /**
   * Whole-recipe totals across every line.
   *
   * @param lines each line's amount paired with its resolved ingredient
   * @return the totals, or {@code null} if the recipe has no lines or any line is unresolved
   */
  public static NutritionTotals totals(List<ResolvedLine> lines) {
    if (lines == null || lines.isEmpty()) {
      return null;
    }
    BigDecimal calories = BigDecimal.ZERO;
    BigDecimal protein = BigDecimal.ZERO;
    BigDecimal carbohydrate = BigDecimal.ZERO;
    BigDecimal fat = BigDecimal.ZERO;
    BigDecimal fiber = BigDecimal.ZERO;

    for (ResolvedLine line : lines) {
      LineNutrition value = lineNutrition(line.amount(), line.ingredient());
      if (value == null) {
        // One unknown line invalidates the whole recipe: see the class comment.
        return null;
      }
      calories = calories.add(value.calories());
      protein = protein.add(value.proteinG());
      carbohydrate = carbohydrate.add(value.carbohydrateG());
      fat = fat.add(value.fatG());
      fiber = fiber.add(value.fiberG());
    }

    return new NutritionTotals(
        scale(calories), scale(protein), scale(carbohydrate), scale(fat), scale(fiber));
  }

  private static BigDecimal scale(BigDecimal value) {
    return value.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
  }

  /** One recipe line: the quantity and the ingredient it resolves to. */
  public record ResolvedLine(BigDecimal amount, Ingredient ingredient) {}

  /** Nutrition of a single line, before summing. */
  public record LineNutrition(
      BigDecimal calories,
      BigDecimal proteinG,
      BigDecimal carbohydrateG,
      BigDecimal fatG,
      BigDecimal fiberG) {}

  /** Whole-recipe totals, never per serving. */
  public record NutritionTotals(
      BigDecimal calories,
      BigDecimal proteinG,
      BigDecimal carbohydrateG,
      BigDecimal fatG,
      BigDecimal fiberG) {}
}
