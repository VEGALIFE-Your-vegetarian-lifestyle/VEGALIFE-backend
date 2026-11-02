package com.vegalife.dto.response.subscription;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionUsageResponse {

  private long used;
  private int limit;
  private Instant periodStart;
  private Instant periodEnd;
}
