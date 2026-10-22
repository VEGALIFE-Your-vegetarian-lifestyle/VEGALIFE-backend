package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin listing of dish or ingredient names, including the ones members introduced.
 *
 * <p>Deliberately separate from the member-facing list request: that one is pinned to {@code
 * APPROVED}, while this one must be able to show the review queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminComponentListRequest {

  @Min(value = 0, message = "Page must be >= 0")
  private Integer page;

  @Min(value = 1, message = "Size must be between 1 and 100")
  @Max(value = 100, message = "Size must be between 1 and 100")
  private Integer size;

  private String sort;

  /** Case-insensitive substring filter on name. */
  private String name;

  /** {@code PENDING}, {@code APPROVED}, {@code REJECTED}; blank or absent means every state. */
  private String moderationStatus;

  public int getPage() {
    return page == null ? 0 : page;
  }

  public int getSize() {
    return size == null ? 20 : size;
  }

  public String getSort() {
    return sort == null || sort.isBlank() ? "name,asc" : sort;
  }
}
