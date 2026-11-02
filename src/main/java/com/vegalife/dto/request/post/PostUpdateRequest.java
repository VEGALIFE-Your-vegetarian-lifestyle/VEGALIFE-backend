package com.vegalife.dto.request.post;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update for title/featuredImageUrl/categoryIds/publish: null/omitted fields are left
 * unchanged. {@code content} and {@code rawContent} are required on every edit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostUpdateRequest {

  @Pattern(regexp = "(?s).*\\S.*", message = "Title must not be blank")
  @Size(max = 255, message = "Title must not exceed 255 characters")
  private String title;

  /** Plain text used as the semantic-filtering input. */
  @NotBlank(message = "Content is required")
  private String content;

  /** Rich-text document produced by the frontend editor. */
  @NotNull(message = "Raw content is required")
  private JsonNode rawContent;

  private String featuredImageUrl;

  /** Replaces the whole category set when supplied (BR-CONTENT-004). */
  private Set<UUID> categoryIds;

  /**
   * true queues the post for content filtering and it becomes visible only once the filter passes
   * (BR-FILTER-005 / FR-007); false withdraws it immediately to a private draft and never re-queues
   * (BR-CONTENT-003).
   */
  private Boolean publish;

  @AssertTrue(message = "Raw content must be a JSON object")
  public boolean isRawContentObject() {
    return rawContent == null || rawContent.isObject();
  }
}
