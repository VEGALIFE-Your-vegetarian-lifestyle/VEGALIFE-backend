package com.vegalife.dto.response.admin;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Revenue by plan over the window, ranked by amount. Draw as horizontal bars: a handful of named
 * categories with one measure, which a donut cannot compare honestly.
 *
 * <p>{@code amount} is what was actually charged, read from the ledger — never the catalogue price
 * multiplied by a count, or a price change would retroactively rewrite history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueByPlanResponse {

  private List<Item> items;

  /**
   * One plan's revenue in the window.
   *
   * <p>{@code purchases} is how many packages of this plan were bought — one per succeeded payment
   * — so a client can show volume alongside money. It is a count of packages, not of dong: {@code
   * amount} is the money. {@code active} is the plan's current catalogue state, kept so a
   * deactivated plan that still earned in the window is visible rather than silently dropped.
   */
  public record Item(
      UUID planId, String code, String name, long amount, long purchases, boolean active) {}
}
