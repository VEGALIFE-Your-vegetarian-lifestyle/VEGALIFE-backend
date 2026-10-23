package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.ModerationStatus;
import com.vegalife.model.recipe.Recipe;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}
