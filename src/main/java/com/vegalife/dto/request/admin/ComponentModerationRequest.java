package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reason supplied when an Administrator approves or rejects a dish or ingredient name directly.
 *
 * <p>One type for both verbs, unlike posts: for a name, approve and reject are symmetric state
 * changes with no side effects beyond the row itself, so the body carries no information the
 * endpoint does not already have.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentModerationRequest {

  /** Why the decision was taken; optional for the caller, stored on the moderation log. */
  @Size(max = 1000, message = "reason must be at most 1000 characters")
  private String reason;
}
