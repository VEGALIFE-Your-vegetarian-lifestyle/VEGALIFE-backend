package com.vegalife.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How many AI requests the member has left (issue #181, ADR-021).
 *
 * <p>One field on purpose. Before allowances stopped expiring this object reported a monthly window
 * ({@code used}, {@code limit}, {@code windowStart}, {@code windowEnd}) and the client subtracted
 * to get a remainder. There is no window any more, and the remaining count is the only number that
 * describes what the member can still do — so it is the only number sent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageQuotaResponse {

  private long remainingUsage;
}
