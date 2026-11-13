package com.vegalife.dto.response.admin;

import com.vegalife.dto.response.post.PostFlagDetailResponse;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin-only view of a post for moderation (ADR-016).
 *
 * <p>Carries everything {@link AdminPostListResponse} does, plus the evidence a reviewer needs to
 * decide: the two relevance scores and the state of every dish and ingredient name the post
 * references. None of it is exposed to members — a score is not a verdict, and showing a creator
 * the number would only invite gaming it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPostDetailResponse {

  private UUID id;
  private String title;
  private String content;
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

  /**
   * Score of the post's own text against the content centroid pair; {@code null} when never scored
   * (a provider failure, not a low score).
   */
  private Double contentScore;

  /** Recipe bodies attached to this post, with their own score. Empty when none is attached. */
  private List<ComponentSummary> recipes;

  /**
   * Every dish and ingredient name this post references, each with its platform-wide state. Derived
   * at read time rather than stored: only "is it approved now?" matters, and a stored snapshot
   * would go stale the moment another post's approval changed the name.
   */
  private List<ComponentSummary> names;

  private UUID userId;
  private String displayName;
  private String email;

  /**
   * One scored or gated component.
   *
   * @param score relevance score, null for names (never scored) and for components that failed to
   *     score
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ComponentSummary {

    private UUID id;

    /** {@code RECIPE}, {@code DISH} or {@code INGREDIENT}. */
    private String type;

    private String name;

    /** {@code PENDING}, {@code APPROVED} or {@code REJECTED}; null for a recipe scored only. */
    private String moderationStatus;

    private Double score;
  }
}
