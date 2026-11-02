package com.vegalife.dto.request.post;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
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

  /** Plain text used as the semantic-filtering input. */
  @NotBlank(message = "Content is required")
  private String content;

  /** Rich-text document produced by the frontend editor. */
  @NotNull(message = "Raw content is required")
  private JsonNode rawContent;

  private String featuredImageUrl;

  private Set<UUID> categoryIds;

  /**
   * false keeps the post as a private draft that is never filtered; true requests publication: the
   * post is queued for content filtering (BR-FILTER-005) and becomes visible only once the filter
   * passes (FR-007).
   */
  private boolean publish;

  @AssertTrue(message = "Raw content must be a JSON object")
  public boolean isRawContentObject() {
    return rawContent == null || rawContent.isObject();
  }
}
