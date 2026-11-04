package com.vegalife.dto.response.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One plan in the public catalogue (issue #181, ADR-021). {@code price.amount == 0} is what makes a
 * plan claimable; there is no separate flag.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailablePlanResponse {

  private String code;
  private String name;
  private String description;
  private int requestQuota;
  private PlanPriceResponse price;
}
