package com.vegalife.dto.response.recipe;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeResponse {

  private UUID id;
  private UUID userId;
  private UUID dishId;
  private String dishName;
  private String name;
  private String description;
  private String instructions;
  private Integer prepTimeMinutes;
  private Integer cookTimeMinutes;
  private Integer servings;
  private String difficulty;
  private List<RecipeIngredientResponse> ingredients;
  private Instant createdAt;
  private Instant updatedAt;
}
