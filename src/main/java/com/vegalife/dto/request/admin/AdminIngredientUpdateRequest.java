package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin partial update of an ingredient name: an omitted field keeps its current value.
 *
 * <p>Editing does not reset the moderation state (BR-DISH-001) — see {@link AdminDishUpdateRequest}
 * for why.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminIngredientUpdateRequest {

  @Size(max = 100, message = "name must be at most 100 characters")
  private String name;
}
