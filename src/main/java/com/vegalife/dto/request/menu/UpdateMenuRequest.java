package com.vegalife.dto.request.menu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input model for {@code PATCH /api/menus/{menuId}}. Meals are replaced wholesale and must form a
 * complete valid week (BR-MENU-005); {@code notes} is optional and keeps its current value when
 * omitted. The window is never part of the payload (BR-MENU-006).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMenuRequest {

  @NotEmpty(message = "days must contain exactly the 7 dates of the week")
  @Valid
  private List<MenuDayRequest> days;

  private String notes;
}
