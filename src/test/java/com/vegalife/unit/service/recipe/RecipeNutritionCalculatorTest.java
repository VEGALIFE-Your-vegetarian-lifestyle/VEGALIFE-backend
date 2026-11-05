package com.vegalife.unit.service.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.enums.recipe.Unit;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.service.recipe.RecipeNutritionCalculator;
import com.vegalife.service.recipe.RecipeNutritionCalculator.NutritionTotals;
import com.vegalife.service.recipe.RecipeNutritionCalculator.ResolvedLine;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic of ADR-022. No Spring, no database: the rule itself is what is under test, so a
 * regression here fails loudly instead of surfacing as a plausible wrong number in a meal plan.
 */
class RecipeNutritionCalculatorTest {

  @Test
  void totals_sumsAmountTimesValueAcrossLines() {
    // 200 g rice + 100 g tofu.
    Ingredient rice = resolved("0.0130", "0.0008", "0.0028", "0.0001", "0.0001");
    Ingredient tofu = resolved("0.0144", "0.0017", "0.0003", "0.0009", "0.0002");

    NutritionTotals totals =
        RecipeNutritionCalculator.totals(
            List.of(
                new ResolvedLine(new BigDecimal("200"), rice),
                new ResolvedLine(new BigDecimal("100"), tofu)));

    assertThat(totals).isNotNull();
    assertThat(totals.calories()).isEqualByComparingTo("4.04");
    assertThat(totals.proteinG()).isEqualByComparingTo("0.33");
  }

  @Test
  void totals_isNullWhenAnyLineIsUnresolved() {
    Ingredient resolved = resolved("0.01", "0.01", "0.01", "0.01", "0.01");
    Ingredient unresolved = Ingredient.builder().id(java.util.UUID.randomUUID()).name("bí").build();

    // The resolved line alone would give 1.00 kcal, which is a plausible-looking lie.
    NutritionTotals totals =
        RecipeNutritionCalculator.totals(
            List.of(
                new ResolvedLine(new BigDecimal("100"), resolved),
                new ResolvedLine(new BigDecimal("50"), unresolved)));

    assertThat(totals).isNull();
  }

  @Test
  void totals_isNullForARecipeWithNoLines() {
    assertThat(RecipeNutritionCalculator.totals(List.of())).isNull();
  }

  @Test
  void totals_zeroNutritionIsResolvedNotMissing() {
    // Water: legitimately 0 across every macro, which must not be mistaken for "unknown".
    Ingredient water = resolved("0", "0", "0", "0", "0");

    NutritionTotals totals =
        RecipeNutritionCalculator.totals(List.of(new ResolvedLine(new BigDecimal("250"), water)));

    assertThat(totals).isNotNull();
    assertThat(totals.calories()).isEqualByComparingTo("0.00");
    assertThat(totals.fiberG()).isEqualByComparingTo("0.00");
  }

  @Test
  void totals_roundsOnceAtTheEndRatherThanPerLine() {
    // 0.005 per line would round to 0.01 three times (0.03) if rounding happened early.
    Ingredient tiny = resolved("0.00005", "0", "0", "0", "0");

    NutritionTotals totals =
        RecipeNutritionCalculator.totals(
            List.of(
                new ResolvedLine(new BigDecimal("100"), tiny),
                new ResolvedLine(new BigDecimal("100"), tiny),
                new ResolvedLine(new BigDecimal("100"), tiny)));

    assertThat(totals).isNotNull();
    assertThat(totals.calories()).isEqualByComparingTo("0.02");
  }

  @Test
  void totals_ingredientWithUnitButNoNutritionIsUnresolved() {
    Ingredient noNutrition =
        Ingredient.builder().id(java.util.UUID.randomUUID()).name("muối").unit(Unit.G).build();

    assertThat(noNutrition.hasResolvedNutrition()).isFalse();
    assertThat(
            RecipeNutritionCalculator.totals(
                List.of(new ResolvedLine(BigDecimal.TEN, noNutrition))))
        .isNull();
  }

  private static Ingredient resolved(
      String calories, String protein, String carbs, String fat, String fiber) {
    return Ingredient.builder()
        .id(java.util.UUID.randomUUID())
        .name("test")
        .unit(Unit.G)
        .calories(new BigDecimal(calories))
        .proteinG(new BigDecimal(protein))
        .carbohydrateG(new BigDecimal(carbs))
        .fatG(new BigDecimal(fat))
        .fiberG(new BigDecimal(fiber))
        .build();
  }
}
