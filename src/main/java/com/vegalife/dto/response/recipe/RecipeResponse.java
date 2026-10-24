package com.vegalife.dto.response.recipe;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
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

  private String name;
  private String description;
  private String instructions;
  private Integer prepTimeMinutes;
  private Integer cookTimeMinutes;
  private Integer servings;
  private String difficulty;
  private List<RecipeIngredientResponse> ingredients;

  /**
   * Author-entered totals for the whole recipe (issue #163), never per serving. {@code null} means
   * the author did not provide the value — distinct from 0.
   */
  private BigDecimal calories;
  private BigDecimal proteinG;
  private BigDecimal carbohydrateG;
  private BigDecimal fatG;
  private BigDecimal fiberG;

  private Instant createdAt;
  private Instant updatedAt;
}
