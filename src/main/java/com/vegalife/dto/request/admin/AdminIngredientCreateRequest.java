package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin creation of an ingredient name.
 *
 * <p>An admin creating a name is approving it, so no {@code moderationStatus} field is accepted
 * here. Carries no nutrition values: since issue #163 nutrition is an author-entered total on
 * {@code recipe}, not a per-ingredient figure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminIngredientCreateRequest {

  @NotBlank(message = "name is required")
  @Size(max = 100, message = "name must be at most 100 characters")
  private String name;
}
