package com.vegalife.unit.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.mapper.post.PostMapper;
import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.request.post.PostListRequest;
import com.vegalife.dto.request.post.PostUpdateRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.post.PostService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

  @Mock private PostRepository postRepository;

  @Mock private UserRepository userRepository;

  @Mock private CategoryRepository categoryRepository;

  @Mock private ModerationLogRepository moderationLogRepository;

  @Mock private OutboundMessageRepository outboundMessageRepository;

  @Mock private PostMapper postMapper;

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private PostService postService;

  private UUID userId;
  private UUID categoryId;
  private User user;
  private Category category;
  private PostCreateRequest request;
  private Post post;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    categoryId = UUID.randomUUID();
    user = User.builder().id(userId).username("postowner").build();
    category = Category.builder().id(categoryId).name("Recipes").build();
    request =
        PostCreateRequest.builder()
            .title("Vegan tofu bowl")
            .content("A simple plant-based lunch.")
            .rawContent(objectMapper.createObjectNode())
            .featuredImageUrl("https://example.com/tofu-bowl.jpg")
            .build();
    post =
        Post.builder()
            .id(UUID.randomUUID())
            .title(request.getTitle())
            .content(request.getContent())
            .rawContent(request.getRawContent())
            .featuredImageUrl(request.getFeaturedImageUrl())
            .build();
  }

  @Test
  void createPost_savesDraftWhenNotPublishing() {
    PostListResponse expectedResponse =
        PostListResponse.builder().title(request.getTitle()).status("created").viewCount(0).build();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(postMapper.toEntity(request)).thenReturn(post);
    when(postRepository.saveAndFlush(post)).thenReturn(post);
    when(postMapper.toListResponse(post)).thenReturn(expectedResponse);

    PostListResponse response = postService.createPost(userId, request);

    assertThat(response).isSameAs(expectedResponse);
    assertThat(post.getUser()).isSameAs(user);
    assertThat(post.getStatus()).isEqualTo(Post.Status.created);
    assertThat(post.getViewCount()).isZero();
    assertThat(post.getPublishedAt()).isNull();
    assertThat(post.getFlag()).isNull();
    verify(postRepository).saveAndFlush(post);
    verifyContentFilterNotQueued();
  }

  @Test
  void createPost_publishQueuesPostForFiltering() {
    request.setPublish(true);
    request.setCategoryIds(Set.of(categoryId));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(categoryRepository.findByIdInAndDeletedAtIsNull(Set.of(categoryId)))
        .thenReturn(List.of(category));
    when(postMapper.toEntity(request)).thenReturn(post);
    when(postRepository.saveAndFlush(post)).thenReturn(post);

    postService.createPost(userId, request);

    assertThat(post.getStatus()).isEqualTo(Post.Status.created);
    assertThat(post.getPublishedAt()).isNull();
    assertThat(post.getFlag()).isEqualTo(Post.Flag.PENDING);
    assertThat(post.getCategories()).containsExactly(category);
    assertContentFilterQueued(post);
  }

  @Test
  void createPost_rejectsPublishWithoutCategory() {
    request.setPublish(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> postService.createPost(userId, request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("At least one category is required to publish a post");

    verify(postRepository, never()).saveAndFlush(post);
    verifyContentFilterNotQueued();
  }

  @Test
  void createPost_rejectsInactiveOrUnknownCategory() {
    request.setCategoryIds(Set.of(categoryId));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(categoryRepository.findByIdInAndDeletedAtIsNull(Set.of(categoryId))).thenReturn(List.of());

    assertThatThrownBy(() -> postService.createPost(userId, request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("One or more categories do not exist or are inactive");
  }

  @Test
  void createPost_throwsWhenAuthenticatedUserNoLongerExists() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.createPost(userId, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");

    verify(postMapper, never()).toEntity(request);
    verify(postRepository, never()).saveAndFlush(post);
  }

  @Test
  void updatePost_updatesOnlySuppliedFieldsAndPreservesSystemFields() {
    UUID postId = UUID.randomUUID();
    Post existingPost =
        Post.builder()
            .id(postId)
            .user(user)
            .title("Old title")
            .content("Keep this content")
            .rawContent(objectMapper.createObjectNode())
            .featuredImageUrl("https://example.com/old.jpg")
            .categories(new java.util.HashSet<>(Set.of(category)))
            .status(Post.Status.published)
            .viewCount(12)
            .build();
    PostUpdateRequest updateRequest =
        PostUpdateRequest.builder()
            .title("New title")
            .content("Keep this content")
            .rawContent(objectMapper.createObjectNode())
            .build();
    PostListResponse expectedResponse = PostListResponse.builder().title("New title").build();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existingPost));
    when(postRepository.saveAndFlush(existingPost)).thenReturn(existingPost);
    when(postMapper.toListResponse(existingPost)).thenReturn(expectedResponse);

    PostListResponse response = postService.updatePost(userId, false, postId, updateRequest);

    assertThat(response).isSameAs(expectedResponse);
    assertThat(existingPost.getTitle()).isEqualTo("New title");
    assertThat(existingPost.getContent()).isEqualTo("Keep this content");
    assertThat(existingPost.getFeaturedImageUrl()).isEqualTo("https://example.com/old.jpg");
    assertThat(existingPost.getUser()).isSameAs(user);
    assertThat(existingPost.getStatus()).isEqualTo(Post.Status.published);
    assertThat(existingPost.getViewCount()).isEqualTo(12);
    assertThat(existingPost.getFlag()).isEqualTo(Post.Flag.PENDING);
    verify(postRepository).saveAndFlush(existingPost);
    assertContentFilterQueued(existingPost);
  }

  @Test
  void updatePost_throwsWhenPostDoesNotBelongToAuthenticatedUserOrIsDeleted() {
    UUID postId = UUID.randomUUID();
    PostUpdateRequest updateRequest =
        PostUpdateRequest.builder().title("New title").content("New content").build();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.updatePost(userId, false, postId, updateRequest))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");

    verify(postRepository, never()).saveAndFlush(post);
  }

  @Test
  void getPost_publishedPostIsReturnedRegardlessOfOwnership() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.published);
    PostListResponse expectedResponse = PostListResponse.builder().id(postId).build();
    when(postRepository.findDetailById(postId)).thenReturn(Optional.of(existing));
    when(postMapper.toListResponse(existing)).thenReturn(expectedResponse);

    PostListResponse response = postService.getPost(postId);

    assertThat(response).isSameAs(expectedResponse);
  }

  @Test
  void getPost_nonPublishedPostIsReturned() {
    UUID postId = UUID.randomUUID();
    Post draft = ownedPost(postId, Post.Status.created);
    PostListResponse expectedResponse = PostListResponse.builder().id(postId).build();
    when(postRepository.findDetailById(postId)).thenReturn(Optional.of(draft));
    when(postMapper.toListResponse(draft)).thenReturn(expectedResponse);

    PostListResponse response = postService.getPost(postId);

    assertThat(response).isSameAs(expectedResponse);
  }

  @Test
  void getPost_deletedOrUnknownPostThrowsNotFound() {
    UUID postId = UUID.randomUUID();
    when(postRepository.findDetailById(postId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.getPost(postId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }

  private Post ownedPost(UUID postId, Post.Status status) {
    return Post.builder()
        .id(postId)
        .user(user)
        .title("Title")
        .content("Body")
        .status(status)
        .viewCount(0)
        .categories(new java.util.HashSet<>(Set.of(category)))
        .build();
  }

  private void assertContentFilterQueued(Post expected) {
    verify(outboundMessageRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                message ->
                    message.getChannel() == OutboundChannel.CONTENT_FILTER
                        && expected.getId().toString().equals(message.getRecipient())
                        && message.getStatus() == OutboundStatus.PENDING
                        && message.getAttempts() == 0
                        && message.getNextAttemptAt() != null
                        && message.getPayload() != null
                        && message.getPayload().contains(expected.getId().toString())));
  }

  private void verifyContentFilterNotQueued() {
    verify(outboundMessageRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void updatePost_publishQueuesDraftForFiltering() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.created);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, false, postId, PostUpdateRequest.builder().content("Body").publish(true).build());

    assertThat(existing.getStatus()).isEqualTo(Post.Status.created);
    assertThat(existing.getPublishedAt()).isNull();
    assertThat(existing.getFlag()).isEqualTo(Post.Flag.PENDING);
    assertContentFilterQueued(existing);
  }

  @Test
  void updatePost_rejectsPublishWithoutCategory() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.created);
    existing.setCategories(new java.util.HashSet<>());
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                postService.updatePost(
                    userId,
                    false,
                    postId,
                    PostUpdateRequest.builder().content("Body").publish(true).build()))
        .isInstanceOf(ValidationException.class)
        .hasMessage("At least one category is required to publish a post");
    verifyContentFilterNotQueued();
  }

  @Test
  void updatePost_rejectsRemovingLastCategoryFromPublishedPost() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.published);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                postService.updatePost(
                    userId,
                    false,
                    postId,
                    PostUpdateRequest.builder().content("Body").categoryIds(Set.of()).build()))
        .isInstanceOf(ValidationException.class);
    verify(postRepository, never()).saveAndFlush(existing);
  }

  @Test
  void updatePost_unpublishReturnsPostToDraft() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.published);
    existing.setPublishedAt(java.time.Instant.now());
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, false, postId, PostUpdateRequest.builder().content("Body").publish(false).build());

    assertThat(existing.getStatus()).isEqualTo(Post.Status.created);
    assertThat(existing.getPublishedAt()).isNull();
    assertThat(existing.getFlag()).isNull();
    verifyContentFilterNotQueued();
  }

  @Test
  void updatePost_contentChangeOnPublishedPostRequeuesForFiltering() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.published);
    existing.setPublishedAt(java.time.Instant.now());
    existing.setFlag(Post.Flag.PASSED);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, false, postId, PostUpdateRequest.builder().content("New body").build());

    assertThat(existing.getStatus()).isEqualTo(Post.Status.published);
    assertThat(existing.getFlag()).isEqualTo(Post.Flag.PENDING);
    assertContentFilterQueued(existing);
  }

  @Test
  void updatePost_contentChangeOnFlaggedPostRequeuesForFiltering() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.flagged);
    existing.setFlag(Post.Flag.REJECTED);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, false, postId, PostUpdateRequest.builder().content("Revised body").build());

    assertThat(existing.getStatus()).isEqualTo(Post.Status.flagged);
    assertThat(existing.getFlag()).isEqualTo(Post.Flag.PENDING);
    assertContentFilterQueued(existing);
  }

  @Test
  void updatePost_contentChangeOnPendingDraftIsNotRequeued() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.created);
    existing.setFlag(Post.Flag.PENDING);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId,
        false,
        postId,
        PostUpdateRequest.builder().title("New title").content("Body").build());

    assertThat(existing.getStatus()).isEqualTo(Post.Status.created);
    assertThat(existing.getFlag()).isEqualTo(Post.Flag.PENDING);
    verifyContentFilterNotQueued();
  }

  @Test
  void updatePost_withdrawWithContentChangeDoesNotRequeue() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.published);
    existing.setPublishedAt(java.time.Instant.now());
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId,
        false,
        postId,
        PostUpdateRequest.builder().title("Withdrawn").content("Body").publish(false).build());

    assertThat(existing.getStatus()).isEqualTo(Post.Status.created);
    assertThat(existing.getPublishedAt()).isNull();
    assertThat(existing.getFlag()).isNull();
    verifyContentFilterNotQueued();
  }

  @Test
  void updatePost_withdrawFlaggedPostReturnsToDraftKeepingFlagAndDoesNotRequeue() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.flagged);
    existing.setFlag(Post.Flag.NEEDS_REVIEW);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId,
        false,
        postId,
        PostUpdateRequest.builder().title("Withdrawn").content("Body").publish(false).build());

    assertThat(existing.getStatus()).isEqualTo(Post.Status.created);
    assertThat(existing.getFlag()).isEqualTo(Post.Flag.NEEDS_REVIEW);
    verifyContentFilterNotQueued();
  }

  @Test
  void updatePost_publishTrueOnPublishedPostRequeuesForFiltering() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.published);
    existing.setPublishedAt(java.time.Instant.now());
    existing.setFlag(Post.Flag.PASSED);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, false, postId, PostUpdateRequest.builder().content("Body").publish(true).build());

    assertThat(existing.getStatus()).isEqualTo(Post.Status.published);
    assertThat(existing.getFlag()).isEqualTo(Post.Flag.PENDING);
    assertThat(existing.getPublishedAt()).isNotNull();
    assertContentFilterQueued(existing);
  }

  @Test
  void updatePost_ownerCannotRepublishHiddenPost() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.hidden);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                postService.updatePost(
                    userId,
                    false,
                    postId,
                    PostUpdateRequest.builder().content("Body").publish(true).build()))
        .isInstanceOf(ValidationException.class)
        .hasMessage("A hidden post can only be changed by an administrator");
  }

  @Test
  void updatePost_adminEditingAnotherUsersPostIsRecorded() {
    UUID postId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.created);
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        adminId,
        true,
        postId,
        PostUpdateRequest.builder().title("Moderated").content("Body").build());

    assertThat(existing.getTitle()).isEqualTo("Moderated");
    verify(moderationLogRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                log ->
                    adminId.equals(log.getActorId())
                        && postId.equals(log.getTargetId())
                        && "EDIT_POST".equals(log.getAction())));
  }

  @Test
  void updatePost_adminEditingOwnPostIsNotLogged() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.created);
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, true, postId, PostUpdateRequest.builder().title("Mine").content("Body").build());

    verify(moderationLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deletePost_softDeletesOwnPost() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.published);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.deletePost(userId, false, postId);

    assertThat(existing.getDeletedAt()).isNotNull();
    verify(moderationLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deletePost_throwsWhenNotOwnerOrAlreadyDeleted() {
    UUID postId = UUID.randomUUID();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.deletePost(userId, false, postId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
    verify(postRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deletePost_adminDeletingAnotherUsersPostIsRecorded() {
    UUID postId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.published);
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.deletePost(adminId, true, postId);

    assertThat(existing.getDeletedAt()).isNotNull();
    verify(moderationLogRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                log ->
                    adminId.equals(log.getActorId())
                        && postId.equals(log.getTargetId())
                        && "DELETE_POST".equals(log.getAction())));
  }

  @Test
  void updateVisibility_hidesPostClearsPublishedAtAndLogs() {
    UUID postId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.published);
    existing.setPublishedAt(java.time.Instant.now());
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updateVisibility(adminId, postId, true);

    assertThat(existing.getStatus()).isEqualTo(Post.Status.hidden);
    assertThat(existing.getPublishedAt()).isNull();
    verify(moderationLogRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                log ->
                    adminId.equals(log.getActorId())
                        && postId.equals(log.getTargetId())
                        && "HIDE_POST".equals(log.getAction())));
  }

  @Test
  void updateVisibility_hidingAlreadyHiddenPostIsNoOp() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.hidden);
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(existing));

    postService.updateVisibility(UUID.randomUUID(), postId, true);

    verify(postRepository, never()).saveAndFlush(existing);
    verify(moderationLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void updateVisibility_unhideReturnsPostToDraftAndLogs() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.hidden);
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updateVisibility(UUID.randomUUID(), postId, false);

    assertThat(existing.getStatus()).isEqualTo(Post.Status.created);
    verify(moderationLogRepository)
        .save(org.mockito.ArgumentMatchers.argThat(log -> "UNHIDE_POST".equals(log.getAction())));
  }

  @Test
  void updateVisibility_rejectsUnhidingPostThatIsNotHidden() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, Post.Status.published);
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> postService.updateVisibility(UUID.randomUUID(), postId, false))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Post is not hidden");
  }

  @Test
  void updateVisibility_throwsWhenPostMissingOrDeleted() {
    UUID postId = UUID.randomUUID();
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.updateVisibility(UUID.randomUUID(), postId, true))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }

  @Test
  void listPostsOfUser_guestSeesOnlyPublishedPosts() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<Post> page = new PageImpl<>(List.of(), pageable, 0);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(postRepository.findByUser_IdAndStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
            userId, Post.Status.published, pageable))
        .thenReturn(page);

    postService.listPostsOfUser(null, false, userId, new PostListRequest());

    verify(postRepository, never())
        .findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void listPostsOfUser_ownerAndAdminSeeAllStatuses() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<Post> page = new PageImpl<>(List.of(), pageable, 0);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(postRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable))
        .thenReturn(page);

    postService.listPostsOfUser(userId, false, userId, new PostListRequest());
    postService.listPostsOfUser(UUID.randomUUID(), true, userId, new PostListRequest());

    verify(postRepository, org.mockito.Mockito.times(2))
        .findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable);
  }

  @Test
  void listPostsOfUser_throwsWhenUserMissingOrDeleted() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> postService.listPostsOfUser(null, false, userId, new PostListRequest()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");

    user.setDeletedAt(java.time.Instant.now());
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    assertThatThrownBy(
            () -> postService.listPostsOfUser(null, false, userId, new PostListRequest()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void listFeed_queriesOnlyPublishedPostsNewestFirst() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<Post> page = new PageImpl<>(List.of(), pageable, 0);
    when(postRepository.findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
            Post.Status.published, pageable))
        .thenReturn(page);

    PageResponse<PostListResponse> response = postService.listFeed(new PostListRequest());

    assertThat(response.getTotalElements()).isZero();
    verify(postRepository)
        .findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
            Post.Status.published, pageable);
  }

  @Test
  void listFeed_passesRequestedPageAndSize() {
    Pageable pageable = PageRequest.of(2, 5);
    Page<Post> page = new PageImpl<>(List.of(), pageable, 0);
    when(postRepository.findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
            Post.Status.published, pageable))
        .thenReturn(page);

    postService.listFeed(PostListRequest.builder().page(2).size(5).build());

    verify(postRepository)
        .findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
            Post.Status.published, pageable);
  }
}
