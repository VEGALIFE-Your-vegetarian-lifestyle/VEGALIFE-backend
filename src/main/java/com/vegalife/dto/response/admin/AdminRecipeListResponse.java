package com.vegalife.dto.response.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRecipeListResponse {

  private UUID id;
  private String name;
  private String description;
  private String instructions;
  private Integer prepTimeMinutes;
  private Integer cookTimeMinutes;
  private Integer servings;
  private String difficulty;

  /** Derived from deletedAt (no stored column): ACTIVE or DELETED. */
  private String status;

  /**
   * {@code PENDING}, {@code APPROVED} or {@code REJECTED} (ADR-016). Distinct from {@link #status},
   * which is about deletion: a recipe can be ACTIVE and still awaiting review.
   */
  private String moderationStatus;

  /** Relevance score at submission time; {@code null} means never scored. */
  private Double score;

  private UUID dishId;
  private String dishName;

  /** The shared dish name's own review state; approving this recipe does not change it. */
  private String dishModerationStatus;

  private Set<UUID> categoryIds;
  private List<IngredientItem> ingredients;

  /**
   * Whole-recipe nutrition, read from the persisted columns and not recomputed here (ADR-022).
   *
   * <p>Identical to the member response's fields, so the list an admin filters on is the same
   * figure a member reads. Null means "not computable yet": no partial sum is ever shown.
   */
  private BigDecimal calories;

  private BigDecimal proteinG;
  private BigDecimal carbohydrateG;
  private BigDecimal fatG;
  private BigDecimal fiberG;

  /**
   * True when every ingredient on this recipe is curated, so the totals above are real.
   *
   * <p>Stated explicitly rather than left to be inferred from a null total: it is the field the
   * admin curation queue is driven by, and "no nutrition yet" must not look like "zero calories".
   */
  private boolean nutritionResolved;

  /** Same advisory field as the member response (BR-DISH-002): never blocks, never stored. */
  private List<String> missingRequiredIngredients;

  private Instant createdAt;
  private Instant updatedAt;

  private UUID userId;
  private String displayName;
  private String email;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class IngredientItem {

    private UUID ingredientId;
    private String name;

    /** The shared ingredient name's own review state. */
    private String moderationStatus;

    private BigDecimal amount;

    /** {@code G}, {@code ML} or {@code PIECE}; read from the ingredient, never stored per line. */
    private String unit;

    /**
     * This line's contribution, at scale 2; null when the ingredient is uncurated.
     *
     * <p>Same shape and same computation as the member line, produced by the same helper, so the
     * admin view cannot drift from what a member sees. Rounded per line while the recipe total is
     * rounded once, so these need not sum exactly to {@link AdminRecipeListResponse#getCalories()}.
     */
    private BigDecimal calories;

    private BigDecimal proteinG;
    private BigDecimal carbohydrateG;
    private BigDecimal fatG;
    private BigDecimal fiberG;

    /** True when the five values above are real, rather than unknown. */
    private boolean nutritionResolved;
  }
}
