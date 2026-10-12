package com.vegalife.dto.response.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSummaryResponse {

  private String code;
  private String name;
  private int monthlyRequestLimit;
  private PlanPriceResponse price;
}
