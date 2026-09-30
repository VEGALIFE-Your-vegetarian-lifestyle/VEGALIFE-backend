package com.vegalife.dto.response.admin;

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
public class AdminPostListResponse {

  private UUID id;
  private String title;
  private String type;
  private String content;
  private String featuredImageUrl;
  private String videoUrl;
  private Set<UUID> categoryIds;
  private Set<UUID> mediaIds;
  private String status;

  /** Content filter verdict: PENDING, PASSED, REJECTED, NEEDS_REVIEW; null when never filtered. */
  private String flag;

  private Integer viewCount;
  private Instant publishedAt;
  private Instant createdAt;

  private UUID userId;
  private String username;
  private String email;
}
