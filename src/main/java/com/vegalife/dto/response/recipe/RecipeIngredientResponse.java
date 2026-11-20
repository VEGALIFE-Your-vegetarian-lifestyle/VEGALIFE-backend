package com.vegalife.dto.response.recipe;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One line of a recipe: how much of an ingredient, in the ingredient's own unit, and what that
 * amount contributes.
 *
 * <p>The line's nutrition is {@code amount x ingredient value}, rounded to two decimals, and is
 * {@code null} across all five when the ingredient has not been curated. It is <strong>not</strong>
 * a slice of a partial sum: the recipe totals stay {@code null} while any line is unresolved, so a
 * non-null line value always belongs to a recipe whose totals are non-null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredientResponse {

  private UUID ingredientId;
  private String name;
  private BigDecimal amount;

  /**
   * The ingredient's own unit ({@code G}, {@code ML}, {@code PIECE}); null while it is undecided.
   * Always read from the ingredient rather than stored per line (ADR-022), so it cannot disagree
   * with the basis the nutrition was recorded on.
   */
  private String unit;

  /**
   * This line's contribution to the recipe totals, at scale 2; null when the ingredient is
   * uncurated.
   *
   * <p>Rounded per line while the totals are rounded once over the exact sum, so the visible line
   * values need not add up to the visible total (ADR-022 decision 11). Clients must not assert that
   * they do.
   */
  private BigDecimal calories;

  private BigDecimal proteinG;
  private BigDecimal carbohydrateG;
  private BigDecimal fatG;
  private BigDecimal fiberG;

  /** True when the five values above are real, rather than unknown. */
  private boolean nutritionResolved;
}
