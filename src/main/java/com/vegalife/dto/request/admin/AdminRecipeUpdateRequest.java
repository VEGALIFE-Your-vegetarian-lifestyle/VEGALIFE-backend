package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin partial update of a recipe.
 *
 * <p>No {@code dishName} and no ingredient lines: the dish is a shared name with its own endpoint,
 * and editing a recipe's ingredient list is a different operation (add/remove rows) rather than a
 * field change. No {@code moderationStatus} either — that is what approve/reject are for, so a typo
 * fix cannot quietly re-approve a rejected recipe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRecipeUpdateRequest {

  @Size(max = 255, message = "name must be at most 255 characters")
  private String name;

  @Size(max = 2000, message = "description must be at most 2000 characters")
  private String description;

  @Size(max = 20000, message = "instructions must be at most 20000 characters")
  private String instructions;

  @PositiveOrZero(message = "prepTimeMinutes must be >= 0")
  private Integer prepTimeMinutes;

  @PositiveOrZero(message = "cookTimeMinutes must be >= 0")
  private Integer cookTimeMinutes;

  @Positive(message = "servings must be > 0")
  private Integer servings;

  /** {@code EASY}, {@code MEDIUM} or {@code HARD}. */
  @Size(max = 20, message = "difficulty must be EASY, MEDIUM, or HARD")
  private String difficulty;
}
