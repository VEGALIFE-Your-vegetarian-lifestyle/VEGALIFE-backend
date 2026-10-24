package com.vegalife.dto.response.recipe;

import com.vegalife.dto.response.menu.RecipeSummaryResponse;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One dish suggestion with the recipes worth recommending for it (issue #163).
 *
 * <p>A suggestion only exists when at least one approved recipe of the dish survives the caller's
 * allergy check and filters, so {@link #recommendedRecipes} is never empty.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishSuggestionResponse {

  private UUID id;
  private String name;
  private String description;
  private String imageUrl;
  private String cuisineType;

  /** Best first, at most {@code recipesPerDish}. */
  private List<RecipeSummaryResponse> recommendedRecipes;
}
