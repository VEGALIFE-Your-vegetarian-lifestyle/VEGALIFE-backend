package com.vegalife.repository.recipe;

import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.recipe.Recipe;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository
    extends JpaRepository<Recipe, UUID>, JpaSpecificationExecutor<Recipe> {

  List<Recipe> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

  /**
   * Recipes eligible to be attached to a post (issue #137): existing, not soft-deleted, and already
   * approved. A {@link ModerationStatus#PENDING} or {@link ModerationStatus#REJECTED} recipe is
   * invisible to the public, so linking one to a post would publish an unreviewed body.
   */
  List<Recipe> findByIdInAndDeletedAtIsNullAndModerationStatus(
      Collection<UUID> ids, ModerationStatus moderationStatus);

  /** Recipes currently attached to a post, read from the owning side ({@code Recipe.posts}). */
  List<Recipe> findByPosts_IdAndDeletedAtIsNull(UUID postId);

  /**
   * The (post, recipe) pairs joining a page of posts to their attached, non-deleted recipes — one
   * query for the whole page instead of one per post. Read from the owning side ({@code
   * Recipe.posts}), the same association {@link #findByPosts_IdAndDeletedAtIsNull} uses.
   */
  @Query(
      "select p.id as postId, r.id as recipeId from Recipe r join r.posts p"
          + " where p.id in :postIds and r.deletedAt is null")
  List<PostRecipeId> findRecipeIdsByPostIds(@Param("postIds") Collection<UUID> postIds);

  /** One row of {@link #findRecipeIdsByPostIds}: which recipe is attached to which post. */
  interface PostRecipeId {

    UUID getPostId();

    UUID getRecipeId();
  }

  Optional<Recipe> findByIdAndDeletedAtIsNull(UUID id);

  /** Admin dashboard (issue #12): total non-deleted recipes. */
  long countByDeletedAtIsNull();

  /** Admin dashboard: recipes awaiting moderation review. */
  long countByModerationStatusAndDeletedAtIsNull(ModerationStatus moderationStatus);

  /**
   * Admin dashboard: when the oldest recipe awaiting review was created, so the queue can report
   * age rather than only size. Empty when nothing is waiting.
   */
  @Query(
      "select min(r.createdAt) from Recipe r"
          + " where r.moderationStatus = :status and r.deletedAt is null")
  Optional<Instant> findOldestCreatedAtByModerationStatus(@Param("status") ModerationStatus status);

  /** Owner-scoped lookup for a member delete (BR-RECP-010). */
  Optional<Recipe> findByIdAndUser_IdAndDeletedAtIsNull(UUID id, UUID userId);

  /**
   * Batch lookup of every recipe that could be recommended for the given dishes (issue #163).
   *
   * <p>Deliberately unfiltered beyond visibility and dish membership: allergy matching and the
   * caller's filter params are applied in memory by the service, because the allergy match compares
   * against a JSONB string list that has no portable JPQL predicate. Ordering is left to the caller
   * so the "top N" rule lives in exactly one place.
   *
   * <p>Callers must not pass an empty collection — {@code IN ()} is not valid JPQL.
   */
  @Query(
      """
      SELECT r FROM Recipe r
      WHERE r.dish.id IN :dishIds
        AND r.deletedAt IS NULL
        AND r.moderationStatus = com.vegalife.enums.recipe.ModerationStatus.APPROVED
      """)
  List<Recipe> findApprovedByDishIdIn(@Param("dishIds") Collection<UUID> dishIds);
}
