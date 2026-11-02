package com.vegalife.dto.request.recipe;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredientItem {

  @NotBlank(message = "Ingredient name is required")
  @Size(max = 100, message = "Ingredient name must not exceed 100 characters")
  private String name;

  @NotNull(message = "Ingredient amount is required")
  @Positive(message = "Ingredient amount must be greater than 0")
  @Digits(
      integer = 7,
      fraction = 3,
      message = "Ingredient amount must have at most 3 decimal places")
  private BigDecimal amount;

  @NotBlank(message = "Ingredient unit is required")
  @Size(max = 30, message = "Ingredient unit must not exceed 30 characters")
  private String unit;
}
