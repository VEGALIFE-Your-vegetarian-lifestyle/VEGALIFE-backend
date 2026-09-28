package com.vegalife.dto.request.post;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostVisibilityRequest {

  /** true hides the post (moderation), false lifts the hide. */
  @NotNull(message = "hidden is required")
  private Boolean hidden;
}
