package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostModerationRequest {

  /** The publish state an Administrator is setting. */
  @NotNull(message = "action is required")
  private Action action;

  /** Why the action was taken; optional for the caller, stored on the moderation log. */
  @Size(max = 1000, message = "reason must be at most 1000 characters")
  private String reason;

  public enum Action {
    PUBLISH,
    UNPUBLISH
  }

  public boolean isPublish() {
    return action == Action.PUBLISH;
  }
}
