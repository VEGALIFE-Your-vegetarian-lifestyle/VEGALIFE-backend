package com.vegalife.dto.request.saved;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bounded pagination for the saved-item lists, matching the post lists' contract (BR-POST-003): a
 * page is never unbounded, so one member's collection cannot turn into a full table read.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedListRequest {

  @Min(value = 0, message = "Page must be >= 0")
  private Integer page;

  @Min(value = 1, message = "Size must be between 1 and 100")
  @Max(value = 100, message = "Size must be between 1 and 100")
  private Integer size;

  public int getPage() {
    return page == null ? 0 : page;
  }

  public int getSize() {
    return size == null ? 20 : size;
  }
}
