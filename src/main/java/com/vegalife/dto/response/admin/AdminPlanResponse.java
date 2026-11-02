package com.vegalife.dto.response.admin;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPlanResponse {

  private UUID id;
  private String code;
  private String name;
  private int monthlyRequestLimit;
  private long priceAmount;
  private String priceCurrency;
  private boolean active;
  private int sortOrder;
  private Instant createdAt;
  private Instant updatedAt;
}
