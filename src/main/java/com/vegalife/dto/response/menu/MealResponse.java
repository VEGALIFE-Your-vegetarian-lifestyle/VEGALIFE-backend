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

  /**
   * The dish's representative image, carried alongside {@code dishName} so a meal row can render
   * without a second call. {@code null} when the dish has no image.
   */
  private String imageUrl;

  private Integer servings;
}
