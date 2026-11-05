package com.vegalife.dto.response.ingredient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the ingredient master list — the suggestion source for the create-recipe form's
 * autocomplete.
 *
 * <p>{@code unit} is part of this shape deliberately (ADR-022, decision 10). A recipe line's amount
 * is meaningful only in the ingredient's own unit, and {@code POST /api/recipes} ignores a unit
 * supplied for an existing ingredient. Without the unit here, a member who autocompletes an
 * ingredient cannot know what their amount will mean.
 *
 * <p>The nutrition values describe <strong>one</strong> unit, exactly as the ingredient row stores
 * them. They are not a label for a finished dish: a client multiplies by the amount it is about to
 * submit. Each value is {@code null} when the ingredient is not curated yet, never {@code 0} to
 * mean unknown — water is legitimately 0 kcal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientResponse {

  private UUID id;
  private String name;

  /** {@code G}, {@code ML} or {@code PIECE}; null while undecided. */
  private String unit;

  /** kcal per one {@link #unit}. */
  private BigDecimal calories;

  private BigDecimal proteinG;
  private BigDecimal carbohydrateG;
  private BigDecimal fatG;
  private BigDecimal fiberG;

  /** True only when the unit and all five values are present. */
  private boolean nutritionResolved;

  private Instant createdAt;
}
