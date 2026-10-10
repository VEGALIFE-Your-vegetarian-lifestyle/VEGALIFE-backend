package com.vegalife.dto.request.menu;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raw query parameters for {@code GET /api/menus}. These are resolved into a {@link
 * com.vegalife.service.menu.MenuQueryFilter} by the resolver; validation here covers only the
 * pagination bounds, while period/date/month/from/to rules live in the resolver so one place owns
 * the filter semantics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuQueryRequest {

  private String period;
  private String date;
  private String month;
  private String from;
  private String to;
  private String status;

  @Min(value = 0, message = "Page must be >= 0")
  private Integer page;

  @Min(value = 1, message = "Size must be between 1 and 100")
  @Max(value = 100, message = "Size must be between 1 and 100")
  private Integer size;

  private String sort;

  public int getPage() {
    return page == null ? 0 : page;
  }

  public int getSize() {
    return size == null ? 20 : size;
  }
}
