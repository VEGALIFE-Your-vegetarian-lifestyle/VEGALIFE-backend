package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chart time-range preset. Only the charts endpoint accepts a range — the summary endpoint is a
 * lifetime snapshot and takes no parameters at all.
 *
 * <p>There is deliberately no {@code custom} preset and no {@code from}/{@code to}: arbitrary
 * windows would need a maximum-span cap, a maximum-bucket cap, and a caller-supplied {@code
 * interval} that could desynchronise the series. With three fixed presets the server derives the
 * bucket size and the window is bounded by construction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRequest {

  @Pattern(regexp = "^(day|week|month)$", message = "range must be one of: day, week, month")
  @Builder.Default
  private String range = "week";
}
