package com.vegalife.dto.response.menu;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One recipe recommended for a dish in a meal plan (issue #163).
 *
 * <p>Deliberately narrower than {@code RecipeResponse}: a recommendation is read-only catalogue
 * content, so it carries no owning user, no moderation state and no ingredient lines. The full
 * recipe stays reachable through the recipe endpoints.
 *
 * <p>Nutrition values are author-entered totals for the whole recipe, not per serving, and are
 * {@code null} when the author did not provide them — never {@code 0}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeSummaryResponse {

  private UUID id;
  private String name;
  private String description;
  private Integer prepTimeMinutes;
  private Integer cookTimeMinutes;

  /** Servings the totals below describe. */
  private Integer servings;

  /** {@code EASY}, {@code MEDIUM} or {@code HARD}; null when not given. */
  private String difficulty;

  private BigDecimal calories;
  private BigDecimal proteinG;
  private BigDecimal carbohydrateG;
  private BigDecimal fatG;
  private BigDecimal fiberG;
}
