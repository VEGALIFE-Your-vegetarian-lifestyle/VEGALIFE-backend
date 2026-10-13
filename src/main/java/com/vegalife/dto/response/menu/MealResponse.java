package com.vegalife.dto.response.menu;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealResponse {

  private String mealType;
  private UUID dishId;
  private String dishName;
  private Integer servings;
}
