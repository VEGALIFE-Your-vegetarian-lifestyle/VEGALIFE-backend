package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reason supplied when an Administrator approves or rejects a post (ADR-016).
 *
 * <p>There is no {@code action} field: the verb is the endpoint. Approve and reject do different
 * things beyond visibility — approve also approves the components the post introduced, reject also
 * rejects the ones still awaiting review — so letting a body choose between them was how the old
 * {@code /moderate} endpoint ended up conflating review with publish.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostApprovalRequest {

  /** Why the decision was taken; optional for the caller, stored on the moderation log. */
  @Size(max = 1000, message = "reason must be at most 1000 characters")
  private String reason;
}
