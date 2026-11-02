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

  /** Recipes currently attached to a post, read from the owning side ({@code Recipe.posts}). */
  List<Recipe> findByPosts_IdAndDeletedAtIsNull(UUID postId);

  Optional<Recipe> findByIdAndDeletedAtIsNull(UUID id);

  /** A member's own recipes, newest first. */
  List<Recipe> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

  /** Admin dashboard (issue #12): total non-deleted recipes. */
  long countByDeletedAtIsNull();

  /** Admin dashboard (issue #12): recipes created within a time range. */
  long countByCreatedAtBetweenAndDeletedAtIsNull(Instant from, Instant to);

  /** Admin dashboard (issue #12): recipes awaiting moderation review. */
  long countByModerationStatusAndDeletedAtIsNull(ModerationStatus moderationStatus);

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
