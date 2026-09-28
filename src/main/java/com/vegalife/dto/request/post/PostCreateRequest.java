package com.vegalife.dto.request.post;

import com.vegalife.model.post.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateRequest {

  @NotBlank(message = "Title is required")
  @Size(max = 255, message = "Title must not exceed 255 characters")
  private String title;

  @NotNull(message = "Type is required (blog or video)")
  private Post.Type type;

  /** Required for blog posts (BR-CONTENT-002); optional description for videos. */
  private String content;

  private String featuredImageUrl;

  /** Video posts need either an external link or an uploaded media (BR-CONTENT-002). */
  private String videoUrl;

  private UUID mediaId;

  private Set<UUID> categoryIds;

  /** false keeps the post as a private draft; true publishes it (BR-CONTENT-003). */
  private boolean publish;
}
