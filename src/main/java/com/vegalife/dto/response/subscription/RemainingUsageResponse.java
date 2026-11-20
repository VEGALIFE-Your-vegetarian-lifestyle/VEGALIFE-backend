package com.vegalife.dto.response.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The member's remaining AI requests (issue #181, ADR-021).
 *
 * <p>This is the entire response of {@code GET /api/subscriptions/me}. The allowance total and the
 * lifetime consumption are deliberately not exposed: only the difference is a caller-facing number.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemainingUsageResponse {

  private long remainingUsage;
}
