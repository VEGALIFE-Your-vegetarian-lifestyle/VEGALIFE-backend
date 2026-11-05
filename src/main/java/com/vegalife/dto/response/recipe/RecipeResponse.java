package com.vegalife.dto.response.recipe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeResponse {

  private UUID id;
  private UUID userId;
  private UUID dishId;
  private String dishName;

  /**
   * The dish's review state. Exposed because a recipe whose dish name is still awaiting approval
   * would otherwise look inexplicably withheld to its own author (BR-DISH-001).
   */
  private String dishModerationStatus;

  /** This recipe's own review state, for the same reason. */
  private String moderationStatus;

  /**
   * Why an admin rejected this recipe; null when no reason is recorded — never rejected, or
   * rejected without a reason (the reason is optional, BR-ADMIN-002).
   *
   * <p>Safe to expose here: every endpoint returning this type is scoped to the caller's own
   * recipes, so there is no other audience to leak it to.
   */
  private String rejectionReason;

  private String name;
  private String description;
  private String instructions;
  private Integer prepTimeMinutes;
  private Integer cookTimeMinutes;
  private Integer servings;
  private String difficulty;
  private List<RecipeIngredientResponse> ingredients;

  /**
   * Names of the dish's active required ingredients this recipe does not use (BR-DISH-002).
   *
   * <p>Advisory only: the recipe is valid and saved regardless. Derived at read time, never stored,
   * so an ingredient an admin marks required later shows up on the next read without a backfill.
   * Empty when nothing is missing.
   */
  private List<String> missingRequiredIngredients;

  /**
   * Totals for the whole recipe, derived from its ingredients (ADR-022), never per serving. {@code
   * null} when any line's ingredient has an unresolved unit or missing nutrition - never a partial
   * sum and never 0 for "unknown".
   */
  private BigDecimal calories;

  private BigDecimal proteinG;
  private BigDecimal carbohydrateG;
  private BigDecimal fatG;
  private BigDecimal fiberG;

  /**
   * Whether the five values above are known. Redundant with {@code calories != null} by
   * construction, but stated explicitly so a client does not have to infer "no data yet" from a
   * null it might also read as an omission.
   */
  private boolean nutritionResolved;

  private Instant createdAt;
  private Instant updatedAt;
}
