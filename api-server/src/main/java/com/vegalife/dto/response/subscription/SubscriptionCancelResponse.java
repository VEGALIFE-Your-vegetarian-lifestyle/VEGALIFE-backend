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
public class SubscriptionCancelResponse {

  private String status;
  private Instant cancelledAt;
}
