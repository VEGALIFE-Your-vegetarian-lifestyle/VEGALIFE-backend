package com.vegalife.service.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.mapper.post.PostMapper;
import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.request.post.PostListRequest;
import com.vegalife.dto.request.post.PostUpdateRequest;
import com.vegalife.dto.request.post.RelatedPostsRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.dto.response.post.RelatedPostResponse;
import com.vegalife.enums.outbound.OutboundChannel;
import com.vegalife.enums.outbound.OutboundStatus;
import com.vegalife.enums.post.MediaStatus;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostEmbeddingRepository;
import com.vegalife.repository.post.PostEmbeddingRepository.NearestMatch;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.admin.PlatformSettingsProvider;
import com.vegalife.service.filter.ActiveEmbeddingModel;
import com.vegalife.service.filter.ContentFilterPayload;
import com.vegalife.service.filter.PostFlagDetail;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
  private final UserProfileRepository userProfileRepository;
  private final CategoryRepository categoryRepository;
  private final MediaRepository mediaRepository;
  private final RecipeRepository recipeRepository;
  private final ModerationLogRepository moderationLogRepository;
  private final OutboundMessageRepository outboundMessageRepository;
  private final PostMapper postMapper;
  private final ObjectMapper objectMapper;
  private final PlatformSettingsProvider platformSettingsProvider;
  private final PostEmbeddingRepository postEmbeddingRepository;
  private final ActiveEmbeddingModel activeEmbeddingModel;

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
    // is never filtered. A submitted post is `processing` from the moment it is queued, so
    // "waiting for a verdict" lives on the lifecycle axis, not in `flag` (ADR-023). Only when the
    // content_filter_enabled platform setting is false (deliberate outage mode) does a submitted
    // post skip the filter and land directly in the admin review queue with flag=FILTER_SKIPPED.
    post.setStatus(request.isPublish() ? PostStatus.processing : PostStatus.drafted);
    boolean filterEnabled = platformSettingsProvider.isContentFilterEnabled();

    Post saved = postRepository.saveAndFlush(post);
    if (request.isPublish()) {
      if (filterEnabled) {
        enqueueContentFilter(saved);
      } else {
        markForAdminReview(saved);
      }
    }
    return toResponse(saved, userId);
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
    applyPublishState(post, request.getPublish(), willEnqueue);
    if (willEnqueue) {
      // A new revision invalidates the previous verdict: the post is re-checked from scratch, and
      // the new run overwrites whatever the old one recorded (ADR-025). Until it lands the post
      // carries no flag, and it is out of view for the duration, so nobody reads content that is
      // mid-review.
      post.setStatus(PostStatus.processing);
      post.setFlag(null);
      post.setFlagDetail(null);
    }

    boolean filterEnabled = platformSettingsProvider.isContentFilterEnabled();

    Post saved = postRepository.saveAndFlush(post);
    if (willEnqueue) {
      if (filterEnabled) {
        enqueueContentFilter(saved);
      } else {
        markForAdminReview(saved);
      }
    }
    recordModerationIfAdminOverride(actorId, isAdmin, saved, "EDIT_POST");
    return toResponse(saved, actorId);
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
   * BR-POST-011: a single post is returned for any status; only soft-deleted posts are excluded.
   * Ownership is never checked — another user's post is returned and the caller's own draft is
   * returned too.
   *
   * <p>{@code viewerId} is only used to decide whether the admin rejection reason is included: the
   * author sees why their post was refused, everyone else gets the same payload without it.
   */
  @Transactional(readOnly = true)
  public PostListResponse getPost(UUID viewerId, UUID postId) {
    Post post =
        postRepository
            .findDetailById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    return toResponse(post, attachedRecipeIds(postId), viewerId);
  }

  /**
   * Semantically similar published posts, ranked by embedding cosine similarity (issue #7). Reuses
   * the {@code post_embedding} vectors the content filter already computes at publish time
   * (ADR-015) — no new embedding call is made here. A source post with no stored embedding (never
   * filtered, or filtering disabled) yields an empty result rather than an error.
   */
  @Transactional(readOnly = true)
  public List<RelatedPostResponse> getRelatedPosts(UUID postId, RelatedPostsRequest request) {
    postRepository
        .findByIdAndDeletedAtIsNull(postId)
        .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    Optional<PostEmbeddingRepository.StoredEmbedding> stored =
        postEmbeddingRepository.findStoredEmbedding(postId);
    if (stored.isEmpty()) {
      return List.of();
    }

    List<NearestMatch> matches =
        postEmbeddingRepository.findTopNearestPublished(
            stored.get().vectorLiteral(), stored.get().model(), postId, request.getLimit());
    if (matches.isEmpty()) {
      return List.of();
    }

    List<UUID> matchedIds = matches.stream().map(NearestMatch::postId).toList();
    Map<UUID, Post> postsById =
        postRepository.findWithAuthorByIdIn(matchedIds).stream()
            .collect(Collectors.toMap(Post::getId, p -> p));

    List<RelatedPostResponse> related =
        matches.stream()
            .filter(match -> postsById.containsKey(match.postId()))
            .map(
                match -> {
                  Post post = postsById.get(match.postId());
                  PostListResponse response =
                      postMapper.toListResponse(post, attachedRecipeIds(post.getId()));
                  return RelatedPostResponse.builder()
                      .post(response)
                      .similarityScore(match.similarity())
                      .build();
                })
            .toList();

    // One avatar query for the whole batch rather than one per related post.
    applyAuthorAvatars(related.stream().map(RelatedPostResponse::getPost).toList());
    return related;
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
    return toResponse(saved, attachedRecipeIds(postId), actorId);
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

    return toResponse(post, recipeIds, actorId);
  }

  /**
   * Shapes the posts behind a saved-items page (BR-SAVED-002).
   *
   * <p>Separate from the saved-item query on purpose: that query decides <em>which</em> ids are in
   * the page and in what order, and this one decides what a post looks like on the wire. Keeping
   * them apart means the saved list cannot drift from the feed's post shape.
   *
   * <p>Same as the feed, {@code recipeIds} is empty here — only the single-post and attach-recipe
   * endpoints populate it. Avatars and the author-only rejection reason are resolved in one batch,
   * never per row.
   */
  @Transactional(readOnly = true)
  public Map<UUID, PostListResponse> readPublishedPostsByIds(
      UUID viewerId, Collection<UUID> postIds) {
    if (postIds == null || postIds.isEmpty()) {
      return Map.of();
    }
    List<Post> posts =
        postRepository.findWithAuthorByIdIn(postIds).stream()
            .filter(post -> post.getDeletedAt() == null)
            .filter(post -> post.getStatus() == PostStatus.published)
            .toList();
    if (posts.isEmpty()) {
      return Map.of();
    }
    List<PostListResponse> responses = posts.stream().map(postMapper::toListResponse).toList();
    applyAuthorAvatars(responses);
    applyRejectionReason(responses, posts, viewerId);
    Map<UUID, PostListResponse> byId = new HashMap<>();
    responses.forEach(response -> byId.put(response.getId(), response));
    return byId;
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
   * withdrawing returns it to a private draft from any state it can be in — including {@code
   * processing}, so a post nobody has decided on yet can still be taken back. Nothing is ever
   * flipped to {@code published} here; that happens only when the content filter passes or an admin
   * approves (FR-007, ADR-023).
   *
   * @param willRequeue whether this update also re-queues the post for content filtering, so a
   *     not-yet-published post being re-checked is validated as if it were published
   */
  private void applyPublishState(Post post, Boolean publish, boolean willRequeue) {
    boolean published = post.getStatus() == PostStatus.published;
    boolean willBePublished = publish != null ? publish : (published || willRequeue);
    if (willBePublished && post.getCategories().isEmpty()) {
      throw new ValidationException(CATEGORY_REQUIRED_TO_PUBLISH);
    }
    if (publish == null) {
      return;
    }
    if (!publish && post.getStatus() != PostStatus.drafted) {
      post.setStatus(PostStatus.drafted);
      post.setPublishedAt(null);
      // A withdrawn post is not being held for anyone, and a draft carries no verdict: the old
      // filter result describes content the author has taken back (ADR-025).
      post.setFlag(null);
      post.setFlagDetail(null);
    }
  }

  /**
   * BR-FILTER-005: an explicit {@code publish: true} always queues (and re-queues) for content
   * filtering; a content change re-queues a post that is still meant to be visible — {@code
   * published}, {@code unpublished} (an admin refusal the author is appealing by editing), or a
   * {@code processing} post that is being held. A post that is merely in flight is not queued
   * again: the pending run reads the body at delivery time, so it already sees the edit. A
   * withdrawn post (draft) never re-queues implicitly.
   */
  private boolean shouldEnqueueFilter(Post post, Boolean publish, boolean contentChanged) {
    if (publish != null) {
      return publish;
    }
    if (!contentChanged) {
      return false;
    }
    return post.getStatus() == PostStatus.published
        || post.getStatus() == PostStatus.unpublished
        || (post.getStatus() == PostStatus.processing && post.getFlag() != null);
  }

  /** FR-003: only a title or content change counts as a content change. */
  private boolean isContentChanged(Post post, PostUpdateRequest request) {
    boolean titleChanged =
        request.getTitle() != null && !request.getTitle().equals(post.getTitle());
    boolean contentChanged = !request.getContent().equals(post.getContent());
    return titleChanged || contentChanged;
  }

  /**
   * Filter bypass ({@code content_filter_enabled} platform setting off): hold a post that would
   * have been queued for content filtering at {@code processing} with {@code flag=FILTER_SKIPPED},
   * instead of sending it to the provider — no publication timestamp and no outbound row. An admin
   * resolves it through the moderation endpoint. {@code FILTER_SKIPPED} rather than {@code
   * FILTER_FAILED}: the filter never formed an opinion (ADR-023).
   */
  private void markForAdminReview(Post post) {
    post.setStatus(PostStatus.processing);
    post.setFlag(PostFlag.FILTER_SKIPPED);
    post.setFlagDetail(PostFlagDetail.skipped(PostFlagDetail.CAUSE_DISABLED));
    post.setPublishedAt(null);
    postRepository.saveAndFlush(post);
    log.info("Filter disabled: post {} is held for admin review", post.getId());
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

    // Always the caller's own posts, so the reason is theirs to read.
    return toPage(posts, userId);
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
    boolean callerIsOwner = ownerId.equals(viewerId);
    Page<Post> posts =
        isAdmin || callerIsOwner
            ? postRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId, pageable)
            : postRepository
                .findByUser_IdAndStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
                    ownerId, PostStatus.published, pageable);

    // Only the owner gets the reason: an Administrator browsing this endpoint is not the author,
    // and reads the same reason from the admin surfaces instead.
    return toPage(posts, callerIsOwner ? viewerId : null);
  }

  /** BR-POST-010: the public feed serves every published, non-deleted post, newest first. */
  @Transactional(readOnly = true)
  public PageResponse<PostListResponse> listFeed(PostListRequest request) {
    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
    Page<Post> posts =
        postRepository.findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
            PostStatus.published, pageable);

    // Public feed: no caller, so no reason. Published posts are not rejected ones anyway.
    return toPage(posts, null);
  }

  /**
   * Maps one post and fills in the author avatar, which {@code PostMapper} cannot reach: the avatar
   * lives on {@code UserProfile}, not on {@code User}.
   *
   * <p>The admin rejection reason is included only when {@code viewerId} is the author's own id, or
   * omitted when it is null (a guest). It is deliberately not left to the mapper: this payload is
   * shared with public endpoints, so a value populated at mapping time would leak.
   */
  private PostListResponse toResponse(Post post, UUID viewerId) {
    PostListResponse response = postMapper.toListResponse(post);
    applyAuthorAvatars(List.of(response));
    applyRejectionReason(List.of(response), List.of(post), viewerId);
    return response;
  }

  private PostListResponse toResponse(Post post, Set<UUID> recipeIds, UUID viewerId) {
    PostListResponse response = postMapper.toListResponse(post, recipeIds);
    applyAuthorAvatars(List.of(response));
    applyRejectionReason(List.of(response), List.of(post), viewerId);
    return response;
  }

  /** Page variant: one avatar query for the whole page, never one per post. */
  private PageResponse<PostListResponse> toPage(Page<Post> posts, UUID viewerId) {
    List<PostListResponse> content =
        posts.getContent().stream().map(postMapper::toListResponse).toList();
    applyAuthorAvatars(content);
    applyRejectionReason(content, posts.getContent(), viewerId);
    return PageResponse.<PostListResponse>builder()
        .content(content)
        .page(posts.getNumber())
        .size(posts.getSize())
        .totalElements(posts.getTotalElements())
        .totalPages(posts.getTotalPages())
        .first(posts.isFirst())
        .last(posts.isLast())
        .build();
  }

  /**
   * Reveals each post's admin rejection reason, but only on rows the caller authored.
   *
   * <p>Called after mapping rather than inside it, because {@code PostListResponse} is also the
   * payload of the public single-post, feed and related endpoints. The author is the one person
   * entitled to read why their content was refused; a guest or another member must never see it.
   *
   * <p>Deliberately assigns on both branches instead of only the owner one: if the mapper guard
   * ever regresses and populates the field, clearing it here keeps the leak from reaching a
   * response rather than depending on the guard holding forever.
   */
  private void applyRejectionReason(
      List<PostListResponse> responses, List<Post> posts, UUID viewerId) {
    Map<UUID, String> reasons = new HashMap<>();
    if (viewerId != null) {
      for (Post post : posts) {
        if (post.getRejectionReason() != null) {
          reasons.put(post.getId(), post.getRejectionReason());
        }
      }
    }
    for (PostListResponse response : responses) {
      boolean callerIsAuthor = viewerId != null && viewerId.equals(response.getUserId());
      response.setRejectionReason(callerIsAuthor ? reasons.get(response.getId()) : null);
    }
  }

  /**
   * Resolves every author avatar in the batch with a single query. A member with no profile row yet
   * (BR-PROFILE-003) has no avatar, which is null rather than an error.
   */
  private void applyAuthorAvatars(List<PostListResponse> responses) {
    Set<UUID> authorIds =
        responses.stream()
            .map(PostListResponse::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (authorIds.isEmpty()) {
      return;
    }
    // Built by hand rather than Collectors.toMap: avatars are nullable and toMap rejects nulls.
    Map<UUID, String> avatars = new HashMap<>();
    for (UserProfile profile : userProfileRepository.findByUserIdIn(authorIds)) {
      avatars.put(profile.getUser().getId(), profile.getAvatarUrl());
    }
    responses.forEach(response -> response.setAvatarUrl(avatars.get(response.getUserId())));
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
    boolean allSucceeded = found.stream().allMatch(m -> m.getStatus() == MediaStatus.succeed);
    if (found.size() != ids.size() || !allSucceeded) {
      throw new ValidationException(
          "One or more media do not exist, are deleted, or have not finished uploading");
    }
    return new HashSet<>(found);
  }

  /**
   * Only recipes that exist, are not soft-deleted, and are already {@link
   * ModerationStatus#APPROVED} may be attached (issue #137).
   *
   * <p>A {@code PENDING} recipe is invisible to the public, so attaching it would expose an
   * unreviewed body through a published post; a {@code REJECTED} one is refused outright (ADR-016).
   * Every requested id is checked, including ids that are already attached, so a post can never
   * keep an unapproved recipe simply by never touching it.
   *
   * <p>An ineligible id is reported as not found rather than as a bad request: whether the recipe
   * exists but awaits review, or does not exist at all, is not the caller's business to
   * distinguish, and the caller has no way to prove otherwise.
   */
  private List<Recipe> resolveRecipes(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<Recipe> found =
        recipeRepository.findByIdInAndDeletedAtIsNullAndModerationStatus(
            ids, ModerationStatus.APPROVED);
    if (found.size() != ids.size()) {
      throw new ResourceNotFoundException("One or more recipes are not found or are not approved");
    }
    return found;
  }

  private Set<UUID> attachedRecipeIds(UUID postId) {
    return recipeRepository.findByPosts_IdAndDeletedAtIsNull(postId).stream()
        .map(Recipe::getId)
        .collect(Collectors.toSet());
  }
}
