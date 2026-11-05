package com.vegalife.dto.response.recipe;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
