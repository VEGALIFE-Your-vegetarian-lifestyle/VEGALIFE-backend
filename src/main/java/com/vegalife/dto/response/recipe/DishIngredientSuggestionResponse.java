package com.vegalife.dto.response.recipe;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One ingredient a dish normally contains, as offered to a member writing a recipe (BR-DISH-002).
 *
 * <p>{@code required} is advisory: it says the ingredient is normally mandatory for this dish, not
 * that a recipe omitting it will be refused.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishIngredientSuggestionResponse {

  private UUID ingredientId;
  private String name;
  private boolean required;
}
