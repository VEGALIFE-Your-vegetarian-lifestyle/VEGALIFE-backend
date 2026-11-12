package com.vegalife.service.saved;

import com.vegalife.dto.request.saved.SavedListRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.saved.SavedItem;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.saved.SavedItemRepository;
import com.vegalife.service.post.PostService;
import com.vegalife.service.recipe.RecipeService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved posts and saved recipes for the authenticated member (BR-SAVED-001).
 *
 * <p>Saving is a private bookmark, not a like: nothing here counts saves for a post's author, and a
 * saved list is readable only by the member who made it. One table backs both target types, with
 * two separate actions, so a save is always an explicit choice of "this post" or "this recipe".
 *
 * <p>Saving and unsaving are idempotent. A double tap — or a retried request — must not fail, and
 * must not depend on the client tracking whether the row already exists.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SavedItemService {

  private final SavedItemRepository savedItemRepository;
  private final PostRepository postRepository;
  private final RecipeRepository recipeRepository;
  private final PostService postService;
  private final RecipeService recipeService;

  /**
   * Saves a published post for the caller (BR-SAVED-002).
   *
   * <p>Only a publicly visible post can be saved: bookmarking a draft or an unpublished post would
   * let the caller build a list of content they cannot otherwise read. The check is reported as not
   * found, matching how every other public read treats an invisible post (BR-POST-010) rather than
   * confirming the id exists.
   *
   * <p>Saving twice is a no-op, so {@code savedAt} keeps the first save's timestamp — the answer to
   * "when did I save this" should not move because the client resent a request.
   */
  @Transactional
  public void savePost(UUID userId, UUID postId) {
    postRepository
        .findByIdAndStatusAndDeletedAtIsNull(postId, PostStatus.published)
        .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    if (savedItemRepository.findByUserIdAndPostId(userId, postId).isPresent()) {
      return;
    }
    savedItemRepository.save(SavedItem.builder().userId(userId).postId(postId).build());
    log.debug("User {} saved post {}", userId, postId);
  }

  /** Removes the caller's save of a post; already-unsaved is a no-op (BR-SAVED-001). */
  @Transactional
  public void unsavePost(UUID userId, UUID postId) {
    savedItemRepository.deleteSavedPost(userId, postId);
  }

  /**
   * Saves an approved recipe for the caller (BR-SAVED-002).
   *
   * <p>{@code APPROVED} is the same bar the public recipe surfaces use, so the saved list can never
   * be the one place an unreviewed recipe body is readable.
   */
  @Transactional
  public void saveRecipe(UUID userId, UUID recipeId) {
    recipeRepository
        .findByIdAndDeletedAtIsNull(recipeId)
        .filter(recipe -> recipe.getModerationStatus() == ModerationStatus.APPROVED)
        .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

    if (savedItemRepository.findByUserIdAndRecipeId(userId, recipeId).isPresent()) {
      return;
    }
    savedItemRepository.save(SavedItem.builder().userId(userId).recipeId(recipeId).build());
    log.debug("User {} saved recipe {}", userId, recipeId);
  }

  /** Removes the caller's save of a recipe; already-unsaved is a no-op (BR-SAVED-001). */
  @Transactional
  public void unsaveRecipe(UUID userId, UUID recipeId) {
    savedItemRepository.deleteSavedRecipe(userId, recipeId);
  }

  /**
   * The caller's saved posts, newest save first, keeping only posts still published.
   *
   * <p>The page is built from two queries: one for which saves are in this page, then one batched
   * read for the posts themselves. That keeps the visibility decision in SQL — a page is always
   * full, no matter how many saved posts have since been unpublished — while still returning the
   * same post shape the feed uses.
   */
  @Transactional(readOnly = true)
  public PageResponse<PostListResponse> listSavedPosts(UUID userId, SavedListRequest request) {
    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
    Page<SavedItem> page =
        savedItemRepository.findVisibleSavedPosts(userId, PostStatus.published, pageable);

    List<UUID> postIds = new ArrayList<>();
    for (SavedItem item : page.getContent()) {
      postIds.add(item.getPostId());
    }
    Map<UUID, PostListResponse> posts = postService.readPublishedPostsByIds(userId, postIds);

    List<PostListResponse> content = new ArrayList<>();
    for (SavedItem item : page.getContent()) {
      PostListResponse post = posts.get(item.getPostId());
      if (post != null) {
        content.add(post);
      }
    }
    return toPage(content, page);
  }

  /**
   * The caller's saved recipes, newest save first, keeping only recipes still approved.
   *
   * <p>Same two-query shape as {@link #listSavedPosts}: the page comes from the saves, the bodies
   * from one batched recipe read.
   */
  @Transactional(readOnly = true)
  public PageResponse<RecipeResponse> listSavedRecipes(UUID userId, SavedListRequest request) {
    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
    Page<SavedItem> page =
        savedItemRepository.findVisibleSavedRecipes(userId, ModerationStatus.APPROVED, pageable);

    List<UUID> recipeIds = new ArrayList<>();
    for (SavedItem item : page.getContent()) {
      recipeIds.add(item.getRecipeId());
    }
    Map<UUID, RecipeResponse> recipes = recipeService.readApprovedRecipesByIds(recipeIds);

    List<RecipeResponse> content = new ArrayList<>();
    for (SavedItem item : page.getContent()) {
      RecipeResponse recipe = recipes.get(item.getRecipeId());
      if (recipe != null) {
        content.add(recipe);
      }
    }
    return toPage(content, page);
  }

  /**
   * Carries the saved-item page's paging metadata onto the mapped content.
   *
   * <p>{@code totalElements} stays the count of visible saves, not the count of saves ever made: a
   * client paging through the list must see the same total the pages are drawn from.
   */
  private <T> PageResponse<T> toPage(List<T> content, Page<SavedItem> page) {
    return PageResponse.<T>builder()
        .content(content)
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .first(page.isFirst())
        .last(page.isLast())
        .build();
  }
}
