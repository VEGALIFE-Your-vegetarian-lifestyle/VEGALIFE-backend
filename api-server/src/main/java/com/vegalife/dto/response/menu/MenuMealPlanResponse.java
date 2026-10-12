package com.vegalife.dto.response.menu;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One menu returned by {@code GET /api/menus/details}, carrying its full day-by-day meal detail.
 *
 * <p>Parallel to {@link MenuDetailResponse} rather than a replacement for it: the create, update
 * and status endpoints keep returning that shape, whose meals identify a dish by id and name only.
 * Two shapes rather than one changed shape means no merged response contract moves under an
 * existing consumer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuMealPlanResponse {

  private UUID id;
  private LocalDate startDate;
  private LocalDate endDate;

  /** {@code drafted}, {@code scheduled}, {@code cancelled} or {@code completed}. */
  private String status;

  private String notes;
  private String dietaryPreferences;
  private Instant createdAt;
  private Instant updatedAt;

  /** Days carrying at least one meal; empty for a menu with no {@code menu_detail} rows. */
  private List<MenuMealPlanDayResponse> days;
}
