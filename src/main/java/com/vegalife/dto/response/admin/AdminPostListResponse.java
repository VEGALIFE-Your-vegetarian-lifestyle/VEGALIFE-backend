package com.vegalife.dto.response.admin;

import com.vegalife.dto.response.post.PostFlagDetailResponse;
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
   * The blocker's evidence, as a JSON snapshot (ADR-024): {@code kind} plus the fields that blocker
   * owns — score/outcome/threshold for {@code FILTER_FAILED}, postId/similarity/threshold for
   * {@code SIMILAR_CONTENT}, cause for {@code FILTER_SKIPPED}. {@code null} when the post is not
   * held.
   */
  private PostFlagDetailResponse flagDetail;

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
