package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Adds an ingredient to a dish's curated list (BR-DISH-002).
 *
 * <p>The name is resolved find-or-create the way {@code POST /api/recipes} resolves ingredient
 * names (BR-RECP-002): an existing row is reused, a new one is created approved because the creator
 * is the approver.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDishIngredientCreateRequest {

  @NotBlank(message = "ingredientName is required")
  @Size(max = 100, message = "ingredientName must be at most 100 characters")
  private String ingredientName;

  /** Defaults to {@code false}: an entry is a suggestion unless an admin says otherwise. */
  private Boolean required;
}
