package com.vegalife.dto.response.ai;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Monthly AI allowance state returned alongside every successful send (BR-AI-001). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageQuotaResponse {

  private long used;
  private int limit;
  private Instant windowStart;
  private Instant windowEnd;
}
