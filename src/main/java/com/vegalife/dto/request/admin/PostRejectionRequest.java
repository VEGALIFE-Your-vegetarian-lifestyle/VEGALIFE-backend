package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reason supplied when an Administrator rejects a post (ADR-016).
 *
 * <p>Separate from {@link PostApprovalRequest} even though the bodies are identical today: the two
 * decisions have different semantics, and a shared type would invite the assumption that they are
 * symmetric.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostRejectionRequest {

  /** Why the post was rejected; optional for the caller, stored on the moderation log. */
  @Size(max = 1000, message = "reason must be at most 1000 characters")
  private String reason;
}
