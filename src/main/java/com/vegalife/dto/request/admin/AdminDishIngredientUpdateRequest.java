package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update of a dish ingredient entry. Only {@code required} is changeable: the ingredient
 * identity is fixed by the path, and changing which ingredient an entry points at is a withdraw
 * plus a re-add.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDishIngredientUpdateRequest {

  @NotNull(message = "required is required")
  private Boolean required;
}
