package com.vegalife.service.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.mapper.post.PostMapper;
import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.request.post.PostListRequest;
import com.vegalife.dto.request.post.PostUpdateRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.filter.ContentFilterPayload;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
  private final ModerationLogRepository moderationLogRepository;
  private final OutboundMessageRepository outboundMessageRepository;
  private final PostMapper postMapper;
  private final ObjectMapper objectMapper;

  @Transactional
  public PostListResponse createPost(UUID userId, PostCreateRequest request) {
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    validateTypeSpecificFields(request);
    Set<Category> categories = resolveCategories(request.getCategoryIds());
    if (request.isPublish() && categories.isEmpty()) {
      throw new ValidationException(CATEGORY_REQUIRED_TO_PUBLISH);
    }
    Media media = resolveMedia(request.getMediaId());

    Post post = postMapper.toEntity(request);
    post.setUser(user);
    post.setViewCount(0);
    post.setCategories(categories);
    if (media != null) {
      post.setMedia(new HashSet<>(Set.of(media)));
    }
    if (post.getContent() == null) {
      post.setContent("");
    }

    // BR-CONTENT-003 / FR-007: a post never becomes visible directly. With publish=true it is
    // queued for content filtering (BR-FILTER-005) and the filter callback publishes it once it
    // passes; without it the post stays a private draft that is never filtered. The queued
    // outbound row's creation time is the enqueue clock (BR-FILTER-009).
    post.setStatus(Post.Status.created);
    if (request.isPublish()) {
      post.setFlag(Post.Flag.PENDING);
    }

    Post saved = postRepository.saveAndFlush(post);
    if (request.isPublish()) {
      enqueueContentFilter(saved);
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

    // BR-CONTENT-002: the type is fixed at creation.
    if (request.getType() != null && request.getType() != post.getType()) {
      throw new ValidationException("Post type cannot be changed");
    }
    boolean isVideo = post.getType() == Post.Type.video;
    if (!isVideo && (request.getVideoUrl() != null || request.getMediaId() != null)) {
      throw new ValidationException("A blog post cannot have a video");
    }

    // FR-003: detected before the fields below are overwritten.
    boolean contentChanged = isContentChanged(post, request);

    if (request.getTitle() != null) {
      post.setTitle(request.getTitle());
    }
    if (request.getContent() != null) {
      post.setContent(request.getContent());
    }
    if (request.getFeaturedImageUrl() != null) {
      post.setFeaturedImageUrl(request.getFeaturedImageUrl());
    }
    if (request.getVideoUrl() != null) {
      if (request.getVideoUrl().isBlank()) {
        throw new ValidationException("Video link must not be blank");
      }
      post.setVideoUrl(request.getVideoUrl());
    }
    if (request.getMediaId() != null) {
      post.setMedia(new HashSet<>(Set.of(resolveMedia(request.getMediaId()))));
    }
    if (request.getCategoryIds() != null) {
      post.setCategories(resolveCategories(request.getCategoryIds()));
    }

    boolean willEnqueue = shouldEnqueueFilter(post, request.getPublish(), contentChanged);
    applyPublishState(post, request.getPublish(), isAdmin, willEnqueue);
    if (willEnqueue) {
      post.setFlag(Post.Flag.PENDING);
    }

    Post saved = postRepository.saveAndFlush(post);
    if (willEnqueue) {
      enqueueContentFilter(saved);
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
   * BR-CONTENT-003: a post may be published only when it has the information its type requires and
   * at least one active category; unpublishing returns it to a private draft (also when it was
   * {@code flagged}, so a withdrawn flagged post never re-queues implicitly). A post that is (or
   * becomes, via a content re-queue) published is re-checked so an edit cannot leave it without a
   * category. Nothing is ever flipped to {@code published} here — that happens only when the
   * content filter passes (FR-007 / Phase 9).
   *
   * @param willRequeue whether this update also re-queues the post for content filtering, so a
   *     flagged post being re-checked is validated as if it were published
   */
  private void applyPublishState(Post post, Boolean publish, boolean isAdmin, boolean willRequeue) {
    boolean published = post.getStatus() == Post.Status.published;
    boolean flagged = post.getStatus() == Post.Status.flagged;
    boolean willBePublished = publish != null ? publish : (published || willRequeue);
    if (willBePublished) {
      if (post.getCategories().isEmpty()) {
        throw new ValidationException(CATEGORY_REQUIRED_TO_PUBLISH);
      }
      if (post.getType() == Post.Type.video
          && isBlank(post.getVideoUrl())
          && post.getMedia().isEmpty()) {
        throw new ValidationException("A video post requires a video file or link");
      }
    }
    if (publish == null) {
      return;
    }
    if (post.getStatus() == Post.Status.hidden && !isAdmin) {
      throw new ValidationException("A hidden post can only be changed by an administrator");
    }
    if (!publish && (published || flagged)) {
      post.setStatus(Post.Status.created);
      post.setPublishedAt(null);
    }
  }

  /**
   * BR-FILTER-005: an explicit {@code publish: true} always queues (and re-queues) for content
   * filtering; a content change re-queues a post that is still meant to be visible — status {@code
   * published} or {@code flagged}. A withdrawn post (draft) never re-queues implicitly.
   */
  private boolean shouldEnqueueFilter(Post post, Boolean publish, boolean contentChanged) {
    if (publish != null) {
      return publish;
    }
    return contentChanged
        && (post.getStatus() == Post.Status.published || post.getStatus() == Post.Status.flagged);
  }

  /** FR-003: only a title, content or media change counts as a content change. */
  private boolean isContentChanged(Post post, PostUpdateRequest request) {
    boolean titleChanged =
        request.getTitle() != null && !request.getTitle().equals(post.getTitle());
    boolean contentChanged =
        request.getContent() != null && !request.getContent().equals(post.getContent());
    boolean mediaChanged =
        request.getMediaId() != null
            && post.getMedia().stream()
                .noneMatch(media -> request.getMediaId().equals(media.getId()));
    return titleChanged || contentChanged || mediaChanged;
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

  /** BR-CONTENT-002: a blog needs written content, a video needs a file or a link. */
  private void validateTypeSpecificFields(PostCreateRequest request) {
    if (request.getType() == Post.Type.blog) {
      if (isBlank(request.getContent())) {
        throw new ValidationException("Content is required for a blog post");
      }
      if (!isBlank(request.getVideoUrl()) || request.getMediaId() != null) {
        throw new ValidationException("A blog post cannot have a video");
      }
    } else if (isBlank(request.getVideoUrl()) && request.getMediaId() == null) {
      throw new ValidationException("A video post requires a video file or link");
    }
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

  private Media resolveMedia(UUID mediaId) {
    if (mediaId == null) {
      return null;
    }
    Media media =
        mediaRepository.findByIdInAndDeletedAtIsNull(Set.of(mediaId)).stream()
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
    if (media.getStatus() != Media.Status.succeed) {
      throw new ValidationException("Media upload has not completed");
    }
    return media;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
