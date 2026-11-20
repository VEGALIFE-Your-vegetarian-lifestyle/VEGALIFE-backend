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

  /**
   * The measurement unit, optional.
   *
   * <p>Used <strong>only when this name does not exist yet</strong>: the value becomes the new
   * ingredient's unit, and an admin may correct it later (ADR-022). For an existing ingredient the
   * canonical unit wins and this field is ignored, so one member cannot re-denominate a shared
   * ingredient that other recipes already depend on.
   *
   * <p>Accepted values are {@code G}, {@code ML}, {@code PIECE} and their spelling variants; {@code
   * kg}, {@code tbsp} and other real units are refused, because silently redefining {@code 1 kg} as
   * {@code 1 g} would change the quantity. The field is optional so an existing {@code PIECE}
   * ingredient can be written as {@code "2 cà chua"}; omitting it for an ingredient whose unit is
   * still undecided stores {@code null} and leaves the recipe uncomputable.
   */
  @Size(max = 30, message = "Ingredient unit must not exceed 30 characters")
  private String unit;
}
