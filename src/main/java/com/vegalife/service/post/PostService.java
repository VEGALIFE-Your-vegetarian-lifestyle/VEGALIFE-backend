package com.vegalife.service.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.mapper.post.PostMapper;
import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.request.post.PostListRequest;
import com.vegalife.dto.request.post.PostUpdateRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.filter.ContentFilterPayload;
import com.vegalife.filter.FilterProperties;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

  private static final String CATEGORY_REQUIRED_TO_PUBLISH =
      "At least one category is required to publish a post";

  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final CategoryRepository categoryRepository;
  private final MediaRepository mediaRepository;
  private final RecipeRepository recipeRepository;
  private final ModerationLogRepository moderationLogRepository;
  private final OutboundMessageRepository outboundMessageRepository;
  private final PostMapper postMapper;
  private final ObjectMapper objectMapper;
  private final FilterProperties filterProperties;

  @Transactional
  public PostListResponse createPost(UUID userId, PostCreateRequest request) {
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Set<Category> categories = resolveCategories(request.getCategoryIds());
    if (request.isPublish() && categories.isEmpty()) {
      throw new ValidationException(CATEGORY_REQUIRED_TO_PUBLISH);
    }

    Post post = postMapper.toEntity(request);
    post.setUser(user);
    post.setViewCount(0);
    post.setCategories(categories);

    // BR-CONTENT-003 / FR-007: a post never becomes visible directly. With publish=true and the
    // filter enabled (the default) it is queued for content filtering (BR-FILTER-005) and the
    // filter callback publishes it once it passes; without it the post stays a private draft that
    // is never filtered. The queued outbound row's creation time is the enqueue clock
    // (BR-FILTER-009). Only when app.filter.enabled=false (deliberate outage mode) does a published
    // post skip the filter and land directly in the admin review queue (flag=NEEDS_REVIEW,
    // status=unpublished) with no embedding call.
    post.setStatus(Post.Status.created);
    if (request.isPublish()) {
      post.setFlag(filterProperties.isEnabled() ? Post.Flag.PENDING : Post.Flag.NEEDS_REVIEW);
    }

    Post saved = postRepository.saveAndFlush(post);
    if (request.isPublish()) {
      if (filterProperties.isEnabled()) {
        enqueueContentFilter(saved);
      } else {
        markForAdminReview(saved);
      }
    }
    return postMapper.toListResponse(saved);
  }

  /**
   * BR-CONTENT-001: only the owner or an administrator may edit a post. A non-admin sees other
   * users' posts as not found. BR-ADMIN-002: an administrator's edit of someone else's post is
   * recorded in the moderation log.
   */
  @Transactional
  public PostListResponse updatePost(
      UUID actorId, boolean isAdmin, UUID postId, PostUpdateRequest request) {
    Post post = findManageablePost(actorId, isAdmin, postId);

    // FR-003: detected before the fields below are overwritten.
    boolean contentChanged = isContentChanged(post, request);

    if (request.getTitle() != null) {
      post.setTitle(request.getTitle());
    }
    post.setContent(request.getContent());
    post.setRawContent(request.getRawContent());
    if (request.getFeaturedImageUrl() != null) {
      post.setFeaturedImageUrl(request.getFeaturedImageUrl());
    }
    if (request.getCategoryIds() != null) {
      post.setCategories(resolveCategories(request.getCategoryIds()));
    }

    boolean willEnqueue = shouldEnqueueFilter(post, request.getPublish(), contentChanged);
    applyPublishState(post, request.getPublish(), isAdmin, willEnqueue);
    if (willEnqueue) {
      post.setFlag(filterProperties.isEnabled() ? Post.Flag.PENDING : Post.Flag.NEEDS_REVIEW);
    }

    Post saved = postRepository.saveAndFlush(post);
    if (willEnqueue) {
      if (filterProperties.isEnabled()) {
        enqueueContentFilter(saved);
      } else {
        markForAdminReview(saved);
      }
    }
    recordModerationIfAdminOverride(actorId, isAdmin, saved, "EDIT_POST");
    return postMapper.toListResponse(saved);
  }

  /**
   * BR-CONTENT-001: only the owner or an Administrator may delete a post. Deletion is soft ({@code
   * deletedAt}) so the row is kept. BR-ADMIN-002: an Administrator removing someone else's post is
   * recorded in the moderation log.
   */
  @Transactional
  public void deletePost(UUID actorId, boolean isAdmin, UUID postId) {
    Post post = findManageablePost(actorId, isAdmin, postId);
    post.setDeletedAt(Instant.now());
    Post saved = postRepository.saveAndFlush(post);
    recordModerationIfAdminOverride(actorId, isAdmin, saved, "DELETE_POST");
  }

  /**
   * BR-ADMIN-002: an Administrator may hide any post from the platform and lift the hide. Hiding
   * removes the publication timestamp; lifting returns the post to a private draft so it must be
   * published again under BR-CONTENT-003. Both actions are always recorded in the moderation log.
   */
  @Transactional
  public PostListResponse updateVisibility(UUID adminId, UUID postId, boolean hidden) {
    Post post =
        postRepository
            .findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    boolean currentlyHidden = post.getStatus() == Post.Status.hidden;
    if (hidden == currentlyHidden) {
      if (hidden) {
        return postMapper.toListResponse(post);
      }
      throw new ValidationException("Post is not hidden");
    }

    if (hidden) {
      post.setStatus(Post.Status.hidden);
      post.setPublishedAt(null);
    } else {
      post.setStatus(Post.Status.created);
    }
    Post saved = postRepository.saveAndFlush(post);
    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(adminId)
            .action(hidden ? "HIDE_POST" : "UNHIDE_POST")
            .targetType("POST")
            .targetId(saved.getId())
            .build());
    return postMapper.toListResponse(saved);
  }

  /**
   * BR-POST-011: a single post is returned for any status; only soft-deleted posts are excluded.
   * Ownership is never checked — another user's post is returned and the caller's own draft is
   * returned too.
   */
  @Transactional(readOnly = true)
  public PostListResponse getPost(UUID postId) {
    Post post =
        postRepository
            .findDetailById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    return postMapper.toListResponse(post, attachedRecipeIds(postId));
  }

  /**
   * Replaces the post's whole media set (issue #137). Only media that exists, is not soft-deleted,
   * and has {@code status = succeed} may be attached; any other id fails the whole call with a 400
   * and nothing is written. An empty {@code mediaIds} detaches every attached media.
   */
  @Transactional
  public PostListResponse attachMedia(
      UUID actorId, boolean isAdmin, UUID postId, Set<UUID> mediaIds) {
    Post post = findManageablePost(actorId, isAdmin, postId);
    post.setMedia(resolveMedia(mediaIds));
    Post saved = postRepository.saveAndFlush(post);
    return postMapper.toListResponse(saved, attachedRecipeIds(postId));
  }

  /**
   * Replaces the post's whole recipe set (issue #137). {@code post_recipe} is owned by {@code
   * Recipe.posts}, so the mutation happens from the recipe side rather than on {@code Post} itself;
   * only recipes that exist and are not soft-deleted may be attached. An empty {@code recipeIds}
   * detaches every attached recipe.
   */
  @Transactional
  public PostListResponse attachRecipes(
      UUID actorId, boolean isAdmin, UUID postId, Set<UUID> recipeIds) {
    Post post = findManageablePost(actorId, isAdmin, postId);
    List<Recipe> requested = resolveRecipes(recipeIds);
    List<Recipe> currentlyAttached = recipeRepository.findByPosts_IdAndDeletedAtIsNull(postId);

    // Membership is checked by id, not entity equality: Recipe's Lombok-generated equals/hashCode
    // covers its mutable `posts` collection, which this method is about to mutate.
    for (Recipe recipe : currentlyAttached) {
      if (!recipeIds.contains(recipe.getId())) {
        recipe.getPosts().remove(post);
      }
    }
    for (Recipe recipe : requested) {
      recipe.getPosts().add(post);
    }
    recipeRepository.saveAll(currentlyAttached);
    recipeRepository.saveAll(requested);

    return postMapper.toListResponse(post, recipeIds);
  }

  /** Owner sees own posts; an Administrator sees every non-deleted post. */
  private Post findManageablePost(UUID actorId, boolean isAdmin, UUID postId) {
    return (isAdmin
            ? postRepository.findByIdAndDeletedAtIsNull(postId)
            : postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, actorId))
        .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
  }

  private void recordModerationIfAdminOverride(
      UUID actorId, boolean isAdmin, Post post, String action) {
    if (isAdmin && !post.getUser().getId().equals(actorId)) {
      moderationLogRepository.save(
          ModerationLog.builder()
              .actorId(actorId)
              .action(action)
              .targetType("POST")
              .targetId(post.getId())
              .build());
    }
  }

  /**
   * BR-CONTENT-003: a post may be published only when it has at least one active category;
   * unpublishing returns it to a private draft (also when it was {@code unpublished}, so a
   * withdrawn rejected post never re-queues implicitly). A post that is (or becomes, via a content
   * re-queue) published is re-checked so an edit cannot leave it without a category. Nothing is
   * ever flipped to {@code published} here — that happens only when the content filter passes
   * (FR-007 / Phase 9).
   *
   * @param willRequeue whether this update also re-queues the post for content filtering, so an
   *     unpublished post being re-checked is validated as if it were published
   */
  private void applyPublishState(Post post, Boolean publish, boolean isAdmin, boolean willRequeue) {
    boolean published = post.getStatus() == Post.Status.published;
    boolean unpublished = post.getStatus() == Post.Status.unpublished;
    boolean willBePublished = publish != null ? publish : (published || willRequeue);
    if (willBePublished) {
      if (post.getCategories().isEmpty()) {
        throw new ValidationException(CATEGORY_REQUIRED_TO_PUBLISH);
      }
    }
    if (publish == null) {
      return;
    }
    if (post.getStatus() == Post.Status.hidden && !isAdmin) {
      throw new ValidationException("A hidden post can only be changed by an administrator");
    }
    if (!publish && (published || unpublished)) {
      post.setStatus(Post.Status.created);
      post.setPublishedAt(null);
    }
  }

  /**
   * BR-FILTER-005: an explicit {@code publish: true} always queues (and re-queues) for content
   * filtering; a content change re-queues a post that is still meant to be visible — status {@code
   * published} or {@code unpublished} (a post the filter did not pass). A withdrawn post (draft)
   * never re-queues implicitly.
   */
  private boolean shouldEnqueueFilter(Post post, Boolean publish, boolean contentChanged) {
    if (publish != null) {
      return publish;
    }
    return contentChanged
        && (post.getStatus() == Post.Status.published
            || post.getStatus() == Post.Status.unpublished);
  }

  /** FR-003: only a title or content change counts as a content change. */
  private boolean isContentChanged(Post post, PostUpdateRequest request) {
    boolean titleChanged =
        request.getTitle() != null && !request.getTitle().equals(post.getTitle());
    boolean contentChanged = !request.getContent().equals(post.getContent());
    return titleChanged || contentChanged;
  }

  /**
   * Filter bypass ({@code app.filter.enabled=false}): route a post that would have been queued for
   * content filtering straight to the admin review queue instead — {@code flag=NEEDS_REVIEW},
   * {@code status=unpublished}, no publication timestamp and no outbound row. An admin then acts on
   * it through the moderation endpoint.
   */
  private void markForAdminReview(Post post) {
    post.setFlag(Post.Flag.NEEDS_REVIEW);
    post.setStatus(Post.Status.unpublished);
    post.setPublishedAt(null);
    postRepository.saveAndFlush(post);
    log.info("Filter disabled: forwarded post {} to admin review queue", post.getId());
  }

  /**
   * ADR-005 outbox: enqueue a CONTENT_FILTER row in the caller's transaction so the filter request
   * survives a crash between saving the post and starting the filter (BR-FILTER-005).
   */
  private void enqueueContentFilter(Post post) {
    try {
      OutboundMessage message =
          OutboundMessage.builder()
              .channel(OutboundChannel.CONTENT_FILTER)
              .recipient(post.getId().toString())
              .payload(objectMapper.writeValueAsString(new ContentFilterPayload(post.getId())))
              .status(OutboundStatus.PENDING)
              .attempts(0)
              .nextAttemptAt(Instant.now())
              .build();
      outboundMessageRepository.save(message);
      log.info("Queued content filter for post {} on outbound queue", post.getId());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize content filter payload", e);
    }
  }

  @Transactional(readOnly = true)
  public PageResponse<PostListResponse> listUserPosts(UUID userId, PostListRequest request) {
    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
    Page<Post> posts =
        postRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable);

    return PageResponse.from(posts.map(postMapper::toListResponse));
  }

  /**
   * BR-CONTENT-003 / BR-PUBLIC-001: everyone, including guests, sees a member's published posts;
   * drafts and other non-public states are visible only to the creator and Administrators.
   */
  @Transactional(readOnly = true)
  public PageResponse<PostListResponse> listPostsOfUser(
      UUID viewerId, boolean isAdmin, UUID ownerId, PostListRequest request) {
    userRepository
        .findById(ownerId)
        .filter(owner -> owner.getDeletedAt() == null)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
    Page<Post> posts =
        isAdmin || ownerId.equals(viewerId)
            ? postRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId, pageable)
            : postRepository
                .findByUser_IdAndStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
                    ownerId, Post.Status.published, pageable);

    return PageResponse.from(posts.map(postMapper::toListResponse));
  }

  /** BR-POST-010: the public feed serves every published, non-deleted post, newest first. */
  @Transactional(readOnly = true)
  public PageResponse<PostListResponse> listFeed(PostListRequest request) {
    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
    Page<Post> posts =
        postRepository.findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
            Post.Status.published, pageable);

    return PageResponse.from(posts.map(postMapper::toListResponse));
  }

  /** BR-CONTENT-004: only existing, active categories may be assigned. */
  private Set<Category> resolveCategories(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return new HashSet<>();
    }
    List<Category> found = categoryRepository.findByIdInAndDeletedAtIsNull(ids);
    if (found.size() != ids.size()) {
      throw new ValidationException("One or more categories do not exist or are inactive");
    }
    return new HashSet<>(found);
  }

  /**
   * Only media that exists, is not soft-deleted, and has finished uploading ({@code status =
   * succeed}) may be attached (issue #137).
   */
  private Set<Media> resolveMedia(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return new HashSet<>();
    }
    List<Media> found = mediaRepository.findByIdInAndDeletedAtIsNull(ids);
    boolean allSucceeded = found.stream().allMatch(m -> m.getStatus() == Media.Status.succeed);
    if (found.size() != ids.size() || !allSucceeded) {
      throw new ValidationException(
          "One or more media do not exist, are deleted, or have not finished uploading");
    }
    return new HashSet<>(found);
  }

  /** Only recipes that exist and are not soft-deleted may be attached (issue #137). */
  private List<Recipe> resolveRecipes(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<Recipe> found = recipeRepository.findByIdInAndDeletedAtIsNull(ids);
    if (found.size() != ids.size()) {
      throw new ValidationException("One or more recipes do not exist or are deleted");
    }
    return found;
  }

  private Set<UUID> attachedRecipeIds(UUID postId) {
    return recipeRepository.findByPosts_IdAndDeletedAtIsNull(postId).stream()
        .map(Recipe::getId)
        .collect(Collectors.toSet());
  }
}
