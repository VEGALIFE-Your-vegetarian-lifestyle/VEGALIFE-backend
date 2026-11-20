package com.vegalife.dto.request.recipe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Paging bounds and filters for the caller's own recipes ({@code GET /api/recipes/me}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyRecipeListRequest {

  @Min(value = 0, message = "Page must be >= 0")
  private Integer page;

  @Min(value = 1, message = "Size must be between 1 and 100")
  @Max(value = 100, message = "Size must be between 1 and 100")
  private Integer size;

  /** {@code PENDING}, {@code APPROVED}, or {@code REJECTED}; absent means every state. */
  @Pattern(
      regexp = "^(PENDING|APPROVED|REJECTED)$",
      message = "moderationStatus must be PENDING, APPROVED, or REJECTED")
  private String moderationStatus;

  private UUID dishId;

  @Pattern(regexp = "^(EASY|MEDIUM|HARD)$", message = "Difficulty must be EASY, MEDIUM, or HARD")
  private String difficulty;

  /** Matches when {@code prepTimeMinutes + cookTimeMinutes} is at most this value. */
  @Min(value = 0, message = "maxTotalTimeMinutes must be >= 0")
  private Integer maxTotalTimeMinutes;

  public int getPage() {
    return page == null ? 0 : page;
  }

  public int getSize() {
    return size == null ? 20 : size;
  }
}
