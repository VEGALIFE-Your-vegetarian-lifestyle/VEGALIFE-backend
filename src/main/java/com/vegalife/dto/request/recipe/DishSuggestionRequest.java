package com.vegalife.dto.request.recipe;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query parameters of {@code GET /api/dishes/suggestions} (issue #163).
 *
 * <p>Every recipe-level constraint ({@code difficulty}, times, nutrition) narrows the dishes to
 * those with at least one matching recipe, and the same constraints decide which recipes are
 * recommended — a dish is never shown for a recipe that was filtered out of its own list.
 *
 * <p>There is deliberately no allergy parameter and no sort parameter: allergies always come from
 * the caller's profile, and ordering is fixed so a caller cannot name a nonexistent property.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishSuggestionRequest {

  /** Case-insensitive substring filter on the dish name. */
  private String name;

  /** Exact, case-insensitive cuisine type. */
  private String cuisineType;

  /** {@code EASY}, {@code MEDIUM} or {@code HARD}. */
  private String difficulty;

  @Min(value = 0, message = "maxPrepTime must be >= 0")
  private Integer maxPrepTime;

  @Min(value = 0, message = "maxCookTime must be >= 0")
  private Integer maxCookTime;

  /** Upper bound on prep + cook, with a missing component counted as 0. */
  @Min(value = 0, message = "maxTotalTime must be >= 0")
  private Integer maxTotalTime;

  @DecimalMin(value = "0", message = "minCalories must be >= 0")
  private BigDecimal minCalories;

  @DecimalMin(value = "0", message = "maxCalories must be >= 0")
  private BigDecimal maxCalories;

  @DecimalMin(value = "0", message = "minProtein must be >= 0")
  private BigDecimal minProtein;

  @Min(value = 0, message = "page must be >= 0")
  private Integer page;

  @Min(value = 1, message = "size must be >= 1")
  private Integer size;

  /** Recipes returned per dish; defaults to 3 and is capped server-side. */
  @Min(value = 1, message = "recipesPerDish must be >= 1")
  private Integer recipesPerDish;

  public int getPage() {
    return page == null ? 0 : page;
  }

  public int getSize() {
    return size == null ? 20 : size;
  }

  public int getRecipesPerDish() {
    return recipesPerDish == null ? 3 : recipesPerDish;
  }
}
