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

  /**
   * Author summary (issue: post responses carry user info). {@code userId} is always present for a
   * live post; {@code avatarUrl} is null when the author has no profile row yet (BR-PROFILE-003).
   * Populated for a soft-deleted author's posts exactly as for anyone else — the post itself is
   * what was deleted, not the account.
   */
  private UUID userId;

  private String displayName;
  private String avatarUrl;

  private String title;
  private String content;
  private JsonNode rawContent;
  private String featuredImageUrl;
  private Set<UUID> categoryIds;
  private Set<UUID> mediaIds;
  private Set<UUID> recipeIds;
  private String status;

  /** Content filter verdict: PENDING, PASSED, REJECTED, NEEDS_REVIEW; null when never filtered. */
  private String flag;

  private Integer viewCount;
  private Instant publishedAt;
  private Instant createdAt;
}
