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

  /**
   * Why the post is being held for review: {@code FILTER_FAILED}, {@code SIMILAR_CONTENT} or {@code
   * FILTER_SKIPPED}; null when the post is not held. Only a post at {@code processing} can carry
   * one (ADR-023).
   */
  private String flag;

  /**
   * The blocker's evidence, as a JSON snapshot (ADR-024): {@code kind} plus the fields that blocker
   * owns — score/outcome/threshold for {@code FILTER_FAILED}, postId/similarity/threshold for
   * {@code SIMILAR_CONTENT}, cause for {@code FILTER_SKIPPED}. {@code null} when no blocker is
   * recorded.
   */
  private PostFlagDetailResponse flagDetail;

  /**
   * Why an admin rejected this post, or null if no admin rejection reason is recorded.
   *
   * <p><strong>Owner-scoped.</strong> Populated only when the caller authored the post — this DTO
   * is also the payload of public endpoints, so {@code PostMapper} never sets it and {@code
   * PostService} fills it in for the author alone.
   *
   * <p>Null is ambiguous on purpose: it means "no reason recorded", which covers never-rejected,
   * rejected without a reason (the reason is optional, BR-ADMIN-002), and withheld by the content
   * filter rather than an admin. See {@link #flag} for that last case.
   */
  private String rejectionReason;

  private Integer viewCount;
  private Instant publishedAt;
  private Instant createdAt;
}
