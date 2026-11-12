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
  private String content;
  private String featuredImageUrl;
  private Set<UUID> categoryIds;
  private Set<UUID> mediaIds;
  private String status;

  /**
   * Why the post is being held for review: {@code FILTER_FAILED}, {@code SIMILAR_CONTENT} or {@code
   * FILTER_SKIPPED}; null when the post is not held (ADR-023).
   */
  private String flag;

  /**
   * Detail for {@link #flag}: for {@code SIMILAR_CONTENT} the matched post as {@code
   * SIMILAR_CONTENT_POST_<postId>}; for {@code FILTER_SKIPPED} a short cause code; for {@code
   * FILTER_FAILED} the band score.
   */
  private String flagDetail;

  /**
   * Why an admin rejected this post; null when no reason is recorded (never rejected, rejected
   * without a reason, or withheld by the content filter rather than an admin).
   */
  private String rejectionReason;

  private Integer viewCount;
  private Instant publishedAt;
  private Instant createdAt;

  private UUID userId;
  private String displayName;
  private String email;
}
