package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reason supplied when an Administrator rejects a recipe (ADR-016).
 *
 * <p>The reason is current state, not history: it is stored on {@code recipe.rejection_reason} and
 * surfaced to the author, then cleared when the recipe is approved again. Approval takes no body —
 * there is nothing to record about a decision that removes a reason.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeRejectionRequest {

  /** Why the recipe was rejected; optional for the caller, stored on the recipe. */
  @Size(max = 1000, message = "reason must be at most 1000 characters")
  private String reason;
}
