package com.vegalife.dto.request.recipe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Paging bounds and filters for the public recipe catalogue. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeListRequest {

  @Min(value = 0, message = "Page must be >= 0")
  private Integer page;

  @Min(value = 1, message = "Size must be between 1 and 100")
  @Max(value = 100, message = "Size must be between 1 and 100")
  private Integer size;

  /**
   * Case-insensitive whole-phrase match on the recipe's text, its dish name, or an ingredient name.
   */
  private String keyword;

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
