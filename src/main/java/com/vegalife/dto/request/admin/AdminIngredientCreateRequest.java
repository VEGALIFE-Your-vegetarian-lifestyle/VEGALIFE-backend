package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin creation of an ingredient name.
 *
 * <p>Nutrition values are optional and untouched by moderation: they describe the food, not the
 * acceptability of the name. An admin creating a name is approving it, so no {@code
 * moderationStatus} field is accepted here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminIngredientCreateRequest {

  @NotBlank(message = "name is required")
  @Size(max = 100, message = "name must be at most 100 characters")
  private String name;

  @PositiveOrZero(message = "calories must be >= 0")
  private BigDecimal calories;

  @PositiveOrZero(message = "proteinG must be >= 0")
  private BigDecimal proteinG;

  @PositiveOrZero(message = "carbohydrateG must be >= 0")
  private BigDecimal carbohydrateG;

  @PositiveOrZero(message = "fatG must be >= 0")
  private BigDecimal fatG;

  @PositiveOrZero(message = "fiberG must be >= 0")
  private BigDecimal fiberG;
}
