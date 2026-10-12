package com.vegalife.dto.request.recipe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeCreateRequest {

  @NotBlank(message = "Recipe name is required")
  @Size(max = 255, message = "Recipe name must not exceed 255 characters")
  private String name;

  @NotBlank(message = "Dish name is required")
  @Size(max = 255, message = "Dish name must not exceed 255 characters")
  private String dishName;

  private String description;

  @NotBlank(message = "Instructions are required")
  private String instructions;

  @NotNull(message = "Servings are required")
  @Min(value = 1, message = "Servings must be at least 1")
  private Integer servings;

  @Pattern(regexp = "^(EASY|MEDIUM|HARD)$", message = "Difficulty must be EASY, MEDIUM, or HARD")
  private String difficulty;

  @Min(value = 0, message = "Prep time must be at least 0")
  private Integer prepTimeMinutes;

  @Min(value = 0, message = "Cook time must be at least 0")
  private Integer cookTimeMinutes;

  @NotEmpty(message = "At least one ingredient is required")
  @Size(max = 50, message = "At most 50 ingredients are allowed")
  @Valid
  private List<RecipeIngredientItem> ingredients;
}
