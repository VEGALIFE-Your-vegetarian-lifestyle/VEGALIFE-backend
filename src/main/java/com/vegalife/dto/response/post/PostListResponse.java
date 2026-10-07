package com.vegalife.dto.response.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
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
public class PostListResponse {

  private UUID id;
  private String title;
  private String content;
  private JsonNode rawContent;
  private String featuredImageUrl;
  private Set<UUID> categoryIds;
  private Set<UUID> mediaIds;
  private String status;

  /** Content filter verdict: PENDING, PASSED, REJECTED, NEEDS_REVIEW; null when never filtered. */
  private String flag;

  private Integer viewCount;
  private Instant publishedAt;
  private Instant createdAt;
}
