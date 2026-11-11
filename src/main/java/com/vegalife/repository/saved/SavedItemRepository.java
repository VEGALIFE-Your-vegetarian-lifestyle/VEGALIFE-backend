package com.vegalife.repository.saved;

import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.saved.SavedItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Saved posts and recipes (BR-SAVED-001).
 *
 * <p>The post and recipe list queries are separate rather than one polymorphic query, because each
 * joins a different table to decide visibility. Keeping them apart lets the visibility filter live
 * in SQL, so a page always returns a full page of visible rows instead of a shorter one after
 * in-memory filtering.
 *
 * <p>{@code SavedItem} stores the target as a plain id column, so the joins below are explicit
 * entity joins rather than mapped associations — a save is a membership row, and nothing here needs
 * to navigate from a save to the target on the read paths that already load it separately.
 */
@Repository
public interface SavedItemRepository extends JpaRepository<SavedItem, UUID> {

  Optional<SavedItem> findByUserIdAndPostId(UUID userId, UUID postId);

  Optional<SavedItem> findByUserIdAndRecipeId(UUID userId, UUID recipeId);

  /** Idempotent unsave: a no-op when the row is already gone. */
  @Modifying
  @Query("delete from SavedItem s where s.userId = :userId and s.postId = :postId")
  int deleteSavedPost(@Param("userId") UUID userId, @Param("postId") UUID postId);

  /** Idempotent unsave: a no-op when the row is already gone. */
  @Modifying
  @Query("delete from SavedItem s where s.userId = :userId and s.recipeId = :recipeId")
  int deleteSavedRecipe(@Param("userId") UUID userId, @Param("recipeId") UUID recipeId);

  /**
   * The caller's saved posts that are still publicly visible, newest save first.
   *
   * <p>Publication, not deletion, is the filter (BR-SAVED-002): a post unpublished or hidden after
   * being saved stops appearing, but its save row survives, so it comes back if the post is
   * published again. A soft-deleted post is excluded for the same reason.
   */
  @Query(
      """
      select s from SavedItem s join Post p on p.id = s.postId
      where s.userId = :userId
        and p.status = :status
        and p.deletedAt is null
      order by s.savedAt desc
      """)
  Page<SavedItem> findVisibleSavedPosts(
      @Param("userId") UUID userId, @Param("status") PostStatus status, Pageable pageable);

  /** The caller's saved recipes that are still approved, newest save first (BR-SAVED-002). */
  @Query(
      """
      select s from SavedItem s join Recipe r on r.id = s.recipeId
      where s.userId = :userId
        and r.moderationStatus = :status
        and r.deletedAt is null
      order by s.savedAt desc
      """)
  Page<SavedItem> findVisibleSavedRecipes(
      @Param("userId") UUID userId, @Param("status") ModerationStatus status, Pageable pageable);
}
