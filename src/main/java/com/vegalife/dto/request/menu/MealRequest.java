package com.vegalife.dto.request.menu;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One meal of a create-menu payload. {@code mealType} is a plain string validated against {@link
 * com.vegalife.enums.menu.MealType} so a bad value gets a clear 400 message instead of a parse
 * error.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealRequest {

  @NotBlank(message = "mealType is required")
  private String mealType;

  @NotNull(message = "dishId is required")
  private UUID dishId;

  @NotNull(message = "servings is required")
  @Min(value = 1, message = "servings must be an integer >= 1")
  private Integer servings;
}
