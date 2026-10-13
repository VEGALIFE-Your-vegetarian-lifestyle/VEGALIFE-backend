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
public class SubscriptionHistoryResponse {

  private String planCode;
  private String planName;
  private String status;
  private Instant startedAt;
  private Instant renewalDate;
  private Instant cancelledAt;
  private Instant createdAt;
}
