package com.vegalife.dto.request.menu;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raw query parameters for {@code GET /api/menus/details}. Resolved by {@link
 * com.vegalife.service.menu.MenuQueryFilterResolver} into a {@link
 * com.vegalife.service.menu.MenuQueryFilter}, so date parsing and defaulting stay in one place
 * (NFR-MAINT-001).
 *
 * <p>There is deliberately no {@code status} here: every matching menu carries its own status
 * field, so a meal-plan reader can tell a draft from a live plan without a second query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuMealPlanQueryRequest {

  private String from;
  private String to;
  private UUID menuId;
}
