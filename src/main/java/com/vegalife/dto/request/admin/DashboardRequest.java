package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Admin dashboard time-range filter (issue #12). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRequest {

  @Pattern(
      regexp = "^(day|week|month|custom)$",
      message = "range must be one of: day, week, month, custom")
  @Builder.Default
  private String range = "week";

  /** Required, inclusive, when {@code range} is {@code custom}. */
  private LocalDate from;

  /** Required, inclusive, when {@code range} is {@code custom}. */
  private LocalDate to;

  @AssertTrue(
      message = "from and to are required when range is custom, and from must not be after to")
  public boolean isCustomRangeValid() {
    if (!"custom".equals(range)) {
      return true;
    }
    return from != null && to != null && !from.isAfter(to);
  }
}
