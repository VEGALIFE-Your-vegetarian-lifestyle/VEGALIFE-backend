package com.vegalife.dto.response.recipe;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredientResponse {

  private UUID ingredientId;
  private String name;
  private BigDecimal amount;
  private String unit;
}
