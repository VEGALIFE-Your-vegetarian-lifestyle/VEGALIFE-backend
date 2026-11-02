package com.vegalife.dto.request.menu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input model for {@code POST /api/menus}. The owner is never a field (identity comes from the JWT)
 * and the end date is derived server-side as {@code startDate + 6} (BR-MENU-005).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMenuRequest {

  @NotNull(message = "startDate is required")
  private LocalDate startDate;

  private String notes;

  @NotEmpty(message = "days must contain exactly the 7 dates of the week")
  @Valid
  private List<MenuDayRequest> days;
}
