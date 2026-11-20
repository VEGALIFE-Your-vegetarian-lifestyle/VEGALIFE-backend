package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

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

  private String sort;

  private UUID userId;

  private UUID categoryId;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Instant createdFrom;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Instant createdTo;

  /**
   * {@code PENDING}, {@code APPROVED} or {@code REJECTED}; blank or absent means every state.
   *
   * <p>{@code PENDING} is the review queue — a recipe created through {@code POST /api/recipes}
   * that no admin has decided on yet (ADR-016).
   */
  private String moderationStatus;

  /**
   * {@code ACTIVE} or {@code DELETED}; blank or absent means both. Distinct from {@link
   * #moderationStatus}: this is about soft-deletion, not review. The list returns soft-deleted rows
   * and reports each one's state in its {@code status} field, so this is the filter that makes them
   * reachable on their own.
   */
  private String status;

  private UUID dishId;

  /** {@code EASY}, {@code MEDIUM} or {@code HARD}; blank or absent means every difficulty. */
  private String difficulty;

  /** Keeps recipes whose prep plus cook time is at most this many minutes. */
  @Min(value = 0, message = "maxTotalTimeMinutes must be >= 0")
  private Integer maxTotalTimeMinutes;

  /** Case-insensitive substring over the recipe text, its dish name, or any ingredient name. */
  private String keyword;

  public int getPage() {
    return page == null ? 0 : page;
  }

  public int getSize() {
    return size == null ? 20 : size;
  }

  public String getSort() {
    return sort == null || sort.isBlank() ? "createdAt,desc" : sort;
  }
}
