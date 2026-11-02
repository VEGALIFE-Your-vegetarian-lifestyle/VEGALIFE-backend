package com.vegalife.dto.request.post;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreateRequest {

  @NotBlank(message = "Comment content is required")
  private String content;

  /** Null creates a top-level comment; otherwise this identifies the comment being replied to. */
  private UUID parentId;
}
