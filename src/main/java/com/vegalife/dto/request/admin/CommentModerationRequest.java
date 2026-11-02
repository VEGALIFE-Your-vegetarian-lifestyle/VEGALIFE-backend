package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentModerationRequest {

  /** Why the comment is being moderated; required and stored on the moderation log (issue #6). */
  @NotBlank(message = "reason is required")
  @Size(max = 1000, message = "reason must be at most 1000 characters")
  private String reason;
}
