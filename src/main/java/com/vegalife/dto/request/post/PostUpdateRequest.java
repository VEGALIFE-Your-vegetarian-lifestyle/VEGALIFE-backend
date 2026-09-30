package com.vegalife.dto.request.post;

import com.vegalife.model.post.Post;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Partial update: null/omitted fields are left unchanged. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostUpdateRequest {

  @Pattern(regexp = "(?s).*\\S.*", message = "Title must not be blank")
  @Size(max = 255, message = "Title must not exceed 255 characters")
  private String title;

  @Pattern(regexp = "(?s).*\\S.*", message = "Content must not be blank")
  private String content;

  private String featuredImageUrl;

  /** Video posts only; replaces the current video link. */
  private String videoUrl;

  /** Video posts only; replaces the current uploaded video. */
  private UUID mediaId;

  /** Replaces the whole category set when supplied (BR-CONTENT-004). */
  private Set<UUID> categoryIds;

  /**
   * true queues the post for content filtering and it becomes visible only once the filter passes
   * (BR-FILTER-005 / FR-007); false withdraws it immediately to a private draft and never re-queues
   * (BR-CONTENT-003).
   */
  private Boolean publish;

  /** Only accepted when equal to the current type (BR-CONTENT-002: type is immutable). */
  private Post.Type type;

  @AssertTrue(message = "At least one field must be provided")
  public boolean isUpdateRequestNotEmpty() {
    return title != null
        || content != null
        || featuredImageUrl != null
        || videoUrl != null
        || mediaId != null
        || categoryIds != null
        || publish != null
        || type != null;
  }
}
