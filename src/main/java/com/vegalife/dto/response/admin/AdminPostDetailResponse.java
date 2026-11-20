package com.vegalife.dto.response.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.vegalife.dto.response.post.PostFlagDetailResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin-only view of a post for moderation (ADR-016).
 *
 * <p>Carries the moderation evidence a reviewer needs to decide: the post's content score and the
 * state of every dish and ingredient name it references. None of it is exposed to members — a score
 * is not a verdict, and showing a creator the number would only invite gaming it.
 *
 * <p>It is a strict superset of {@link AdminPostListResponse}: every field the list item carries is
 * present here too. The media fields matter most — under ADR-029 a post with media waits at {@code
 * pending} for exactly that media to be reviewed, so a decision screen without {@code
 * featuredImageUrl} and {@code mediaIds} could not support the decision it exists for.
 *
 * <p>One field deliberately differs in type rather than being duplicated: {@link #recipes} is a
 * list of {@link ComponentSummary} because a reviewer needs each recipe's moderation state, while
 * the list item's {@code recipeIds} is just a set of ids. Both answer different questions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPostDetailResponse {

  private UUID id;

  private UUID userId;
  private String displayName;

  /** Author avatar, resolved from {@code UserProfile}; null when the author has no profile row. */
  private String avatarUrl;

  private String title;
  private String content;

  /** The rich-text editor document as submitted, so a rich post can be reviewed in full. */
  private JsonNode rawContent;

  private String featuredImageUrl;
  private Set<UUID> categoryIds;

  /** Media attached to the post; this is what an admin reviews on a {@code pending} media post. */
  private Set<UUID> mediaIds;

  /** Ids of the attached non-deleted recipes. See {@link #recipes} for their review state. */
  private Set<UUID> recipeIds;

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

  /** Recipe bodies attached to this post. Empty when none is attached. */
  private List<ComponentSummary> recipes;

  /**
   * Every dish and ingredient name this post references, each with its platform-wide state. Derived
   * at read time rather than stored: only "is it approved now?" matters, and a stored snapshot
   * would go stale the moment another post's approval changed the name.
   */
  private List<ComponentSummary> names;

  private Integer viewCount;
  private Instant publishedAt;
  private Instant createdAt;

  private String email;

  /**
   * One component of a post: a recipe body it attaches, or a dish/ingredient name it references.
   * None of them carries a score — only the post body is scored (ADR-026).
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

    /** {@code PENDING}, {@code APPROVED} or {@code REJECTED}. */
    private String moderationStatus;
  }
}
