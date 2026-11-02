package com.vegalife.dto.request.menu;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input model for {@code POST /api/menus/{menuId}/status}. The only field is the target status; the
 * accepted vocabulary is {@code scheduled|cancelled|completed} — {@code drafted} is never a target
 * (menus are born drafted, BR-MENU-005) and any other value is a 400 (BR-MENU-007).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMenuStatusRequest {

  @NotBlank(message = "status must not be blank")
  private String status;
}
