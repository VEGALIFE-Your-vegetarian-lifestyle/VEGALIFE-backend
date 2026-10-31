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
import com.vegalife.dto.request.post.RelatedPostsRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.dto.response.post.RelatedPostResponse;
import com.vegalife.enums.outbound.OutboundChannel;
import com.vegalife.enums.outbound.OutboundStatus;
import com.vegalife.enums.post.MediaStatus;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostEmbeddingRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.filter.ActiveEmbeddingModel;
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

  @Mock private MediaRepository mediaRepository;

  @Mock private RecipeRepository recipeRepository;

  @Mock private ModerationLogRepository moderationLogRepository;

  @Mock private OutboundMessageRepository outboundMessageRepository;

  @Mock private PostMapper postMapper;

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @Mock private com.vegalife.service.filter.FilterProperties filterProperties;

  @Mock private PostEmbeddingRepository postEmbeddingRepository;

  @Mock private ActiveEmbeddingModel activeEmbeddingModel;

  @InjectMocks private PostService postService;

  private UUID userId;
  private UUID categoryId;
  private User user;
  private Category category;
  private PostCreateRequest request;
  private Post post;

  @BeforeEach
  void setUp() {
    // Default the suite to the filter-enabled path; bypass tests override this to false.
    org.mockito.Mockito.lenient().when(filterProperties.isEnabled()).thenReturn(true);
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
    assertThat(post.getStatus()).isEqualTo(PostStatus.created);
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

    assertThat(post.getStatus()).isEqualTo(PostStatus.created);
    assertThat(post.getPublishedAt()).isNull();
    assertThat(post.getFlag()).isEqualTo(PostFlag.PENDING);
    assertThat(post.getCategories()).containsExactly(category);
    assertContentFilterQueued(post);
  }

  @Test
  void createPost_publishForwardsToAdminReviewWhenFilterDisabled() {
    when(filterProperties.isEnabled()).thenReturn(false);
    request.setPublish(true);
    request.setCategoryIds(Set.of(categoryId));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(categoryRepository.findByIdInAndDeletedAtIsNull(Set.of(categoryId)))
        .thenReturn(List.of(category));
    when(postMapper.toEntity(request)).thenReturn(post);
    when(postRepository.saveAndFlush(post)).thenReturn(post);

    postService.createPost(userId, request);

    assertThat(post.getStatus()).isEqualTo(PostStatus.unpublished);
    assertThat(post.getFlag()).isEqualTo(PostFlag.NEEDS_REVIEW);
    assertThat(post.getPublishedAt()).isNull();
    verifyContentFilterNotQueued();
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
            .status(PostStatus.published)
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
    assertThat(existingPost.getStatus()).isEqualTo(PostStatus.published);
    assertThat(existingPost.getViewCount()).isEqualTo(12);
    assertThat(existingPost.getFlag()).isEqualTo(PostFlag.PENDING);
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
    Post existing = ownedPost(postId, PostStatus.published);
    PostListResponse expectedResponse = PostListResponse.builder().id(postId).build();
    when(postRepository.findDetailById(postId)).thenReturn(Optional.of(existing));
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(postId)).thenReturn(List.of());
    when(postMapper.toListResponse(existing, Set.of())).thenReturn(expectedResponse);

    PostListResponse response = postService.getPost(postId);

    assertThat(response).isSameAs(expectedResponse);
  }

  @Test
  void getPost_nonPublishedPostIsReturned() {
    UUID postId = UUID.randomUUID();
    Post draft = ownedPost(postId, PostStatus.created);
    PostListResponse expectedResponse = PostListResponse.builder().id(postId).build();
    when(postRepository.findDetailById(postId)).thenReturn(Optional.of(draft));
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(postId)).thenReturn(List.of());
    when(postMapper.toListResponse(draft, Set.of())).thenReturn(expectedResponse);

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

  @Test
  void getRelatedPosts_returnsNearestPublishedMatchesRankedBySimilarity() {
    UUID postId = UUID.randomUUID();
    UUID matchId = UUID.randomUUID();
    Post source = ownedPost(postId, PostStatus.published);
    Post match = ownedPost(matchId, PostStatus.published);
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(source));
    when(postEmbeddingRepository.findStoredEmbedding(postId))
        .thenReturn(
            Optional.of(new PostEmbeddingRepository.StoredEmbedding("[0.1,0.2]", "model-x")));
    when(postEmbeddingRepository.findTopNearestPublished("[0.1,0.2]", "model-x", postId, 10))
        .thenReturn(List.of(new PostEmbeddingRepository.NearestMatch(matchId, 0.92)));
    when(postRepository.findAllById(List.of(matchId))).thenReturn(List.of(match));
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(matchId)).thenReturn(List.of());
    PostListResponse matchResponse = PostListResponse.builder().id(matchId).build();
    when(postMapper.toListResponse(match, Set.of())).thenReturn(matchResponse);

    List<RelatedPostResponse> related =
        postService.getRelatedPosts(postId, RelatedPostsRequest.builder().build());

    assertThat(related).hasSize(1);
    assertThat(related.get(0).getPost()).isSameAs(matchResponse);
    assertThat(related.get(0).getSimilarityScore()).isEqualTo(0.92);
  }

  @Test
  void getRelatedPosts_sourceWithNoEmbeddingReturnsEmptyList() {
    UUID postId = UUID.randomUUID();
    Post source = ownedPost(postId, PostStatus.published);
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(source));
    when(postEmbeddingRepository.findStoredEmbedding(postId)).thenReturn(Optional.empty());

    List<RelatedPostResponse> related =
        postService.getRelatedPosts(postId, RelatedPostsRequest.builder().build());

    assertThat(related).isEmpty();
  }

  @Test
  void getRelatedPosts_deletedOrUnknownPostThrowsNotFound() {
    UUID postId = UUID.randomUUID();
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> postService.getRelatedPosts(postId, RelatedPostsRequest.builder().build()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }

  private Post ownedPost(UUID postId, PostStatus status) {
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
    Post existing = ownedPost(postId, PostStatus.created);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, false, postId, PostUpdateRequest.builder().content("Body").publish(true).build());

    assertThat(existing.getStatus()).isEqualTo(PostStatus.created);
    assertThat(existing.getPublishedAt()).isNull();
    assertThat(existing.getFlag()).isEqualTo(PostFlag.PENDING);
    assertContentFilterQueued(existing);
  }

  @Test
  void updatePost_rejectsPublishWithoutCategory() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.created);
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
    Post existing = ownedPost(postId, PostStatus.published);
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
    Post existing = ownedPost(postId, PostStatus.published);
    existing.setPublishedAt(java.time.Instant.now());
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, false, postId, PostUpdateRequest.builder().content("Body").publish(false).build());

    assertThat(existing.getStatus()).isEqualTo(PostStatus.created);
    assertThat(existing.getPublishedAt()).isNull();
    assertThat(existing.getFlag()).isNull();
    verifyContentFilterNotQueued();
  }

  @Test
  void updatePost_contentChangeOnPublishedPostRequeuesForFiltering() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.published);
    existing.setPublishedAt(java.time.Instant.now());
    existing.setFlag(PostFlag.PASSED);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, false, postId, PostUpdateRequest.builder().content("New body").build());

    assertThat(existing.getStatus()).isEqualTo(PostStatus.published);
    assertThat(existing.getFlag()).isEqualTo(PostFlag.PENDING);
    assertContentFilterQueued(existing);
  }

  @Test
  void updatePost_contentChangeOnUnpublishedPostRequeuesForFiltering() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.unpublished);
    existing.setFlag(PostFlag.REJECTED);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, false, postId, PostUpdateRequest.builder().content("Revised body").build());

    assertThat(existing.getStatus()).isEqualTo(PostStatus.unpublished);
    assertThat(existing.getFlag()).isEqualTo(PostFlag.PENDING);
    assertContentFilterQueued(existing);
  }

  @Test
  void updatePost_publishForwardsToAdminReviewWhenFilterDisabled() {
    when(filterProperties.isEnabled()).thenReturn(false);
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.created);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, false, postId, PostUpdateRequest.builder().content("Body").publish(true).build());

    assertThat(existing.getStatus()).isEqualTo(PostStatus.unpublished);
    assertThat(existing.getFlag()).isEqualTo(PostFlag.NEEDS_REVIEW);
    assertThat(existing.getPublishedAt()).isNull();
    verifyContentFilterNotQueued();
  }

  @Test
  void updatePost_contentChangeOnPendingDraftIsNotRequeued() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.created);
    existing.setFlag(PostFlag.PENDING);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId,
        false,
        postId,
        PostUpdateRequest.builder().title("New title").content("Body").build());

    assertThat(existing.getStatus()).isEqualTo(PostStatus.created);
    assertThat(existing.getFlag()).isEqualTo(PostFlag.PENDING);
    verifyContentFilterNotQueued();
  }

  @Test
  void updatePost_withdrawWithContentChangeDoesNotRequeue() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.published);
    existing.setPublishedAt(java.time.Instant.now());
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId,
        false,
        postId,
        PostUpdateRequest.builder().title("Withdrawn").content("Body").publish(false).build());

    assertThat(existing.getStatus()).isEqualTo(PostStatus.created);
    assertThat(existing.getPublishedAt()).isNull();
    assertThat(existing.getFlag()).isNull();
    verifyContentFilterNotQueued();
  }

  @Test
  void updatePost_withdrawUnpublishedPostReturnsToDraftKeepingFlagAndDoesNotRequeue() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.unpublished);
    existing.setFlag(PostFlag.NEEDS_REVIEW);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId,
        false,
        postId,
        PostUpdateRequest.builder().title("Withdrawn").content("Body").publish(false).build());

    assertThat(existing.getStatus()).isEqualTo(PostStatus.created);
    assertThat(existing.getFlag()).isEqualTo(PostFlag.NEEDS_REVIEW);
    verifyContentFilterNotQueued();
  }

  @Test
  void updatePost_publishTrueOnPublishedPostRequeuesForFiltering() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.published);
    existing.setPublishedAt(java.time.Instant.now());
    existing.setFlag(PostFlag.PASSED);
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, false, postId, PostUpdateRequest.builder().content("Body").publish(true).build());

    assertThat(existing.getStatus()).isEqualTo(PostStatus.published);
    assertThat(existing.getFlag()).isEqualTo(PostFlag.PENDING);
    assertThat(existing.getPublishedAt()).isNotNull();
    assertContentFilterQueued(existing);
  }

  @Test
  void updatePost_ownerCannotRepublishHiddenPost() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.hidden);
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
    Post existing = ownedPost(postId, PostStatus.created);
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
    Post existing = ownedPost(postId, PostStatus.created);
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updatePost(
        userId, true, postId, PostUpdateRequest.builder().title("Mine").content("Body").build());

    verify(moderationLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deletePost_softDeletesOwnPost() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.published);
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
    Post existing = ownedPost(postId, PostStatus.published);
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
    Post existing = ownedPost(postId, PostStatus.published);
    existing.setPublishedAt(java.time.Instant.now());
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updateVisibility(adminId, postId, true);

    assertThat(existing.getStatus()).isEqualTo(PostStatus.hidden);
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
    Post existing = ownedPost(postId, PostStatus.hidden);
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(existing));

    postService.updateVisibility(UUID.randomUUID(), postId, true);

    verify(postRepository, never()).saveAndFlush(existing);
    verify(moderationLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void updateVisibility_unhideReturnsPostToDraftAndLogs() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.hidden);
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);

    postService.updateVisibility(UUID.randomUUID(), postId, false);

    assertThat(existing.getStatus()).isEqualTo(PostStatus.created);
    verify(moderationLogRepository)
        .save(org.mockito.ArgumentMatchers.argThat(log -> "UNHIDE_POST".equals(log.getAction())));
  }

  @Test
  void updateVisibility_rejectsUnhidingPostThatIsNotHidden() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.published);
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
            userId, PostStatus.published, pageable))
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
            PostStatus.published, pageable))
        .thenReturn(page);

    PageResponse<PostListResponse> response = postService.listFeed(new PostListRequest());

    assertThat(response.getTotalElements()).isZero();
    verify(postRepository)
        .findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
            PostStatus.published, pageable);
  }

  @Test
  void listFeed_passesRequestedPageAndSize() {
    Pageable pageable = PageRequest.of(2, 5);
    Page<Post> page = new PageImpl<>(List.of(), pageable, 0);
    when(postRepository.findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
            PostStatus.published, pageable))
        .thenReturn(page);

    postService.listFeed(PostListRequest.builder().page(2).size(5).build());

    verify(postRepository)
        .findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
            PostStatus.published, pageable);
  }

  @Test
  void attachMedia_replacesWholeSet() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.created);
    UUID mediaId = UUID.randomUUID();
    Media media = Media.builder().id(mediaId).status(MediaStatus.succeed).build();
    PostListResponse expectedResponse = PostListResponse.builder().id(postId).build();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(mediaRepository.findByIdInAndDeletedAtIsNull(Set.of(mediaId))).thenReturn(List.of(media));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(postId)).thenReturn(List.of());
    when(postMapper.toListResponse(existing, Set.of())).thenReturn(expectedResponse);

    PostListResponse response = postService.attachMedia(userId, false, postId, Set.of(mediaId));

    assertThat(response).isSameAs(expectedResponse);
    assertThat(existing.getMedia()).containsExactly(media);
    verify(postRepository).saveAndFlush(existing);
  }

  @Test
  void attachMedia_emptySetDetachesAll() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.created);
    existing.setMedia(
        new java.util.HashSet<>(
            Set.of(Media.builder().id(UUID.randomUUID()).status(MediaStatus.succeed).build())));
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(postRepository.saveAndFlush(existing)).thenReturn(existing);
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(postId)).thenReturn(List.of());
    when(postMapper.toListResponse(existing, Set.of()))
        .thenReturn(PostListResponse.builder().id(postId).build());

    postService.attachMedia(userId, false, postId, Set.of());

    assertThat(existing.getMedia()).isEmpty();
  }

  @Test
  void attachMedia_rejectsMediaThatIsNotSucceeded() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.created);
    UUID mediaId = UUID.randomUUID();
    Media uploading = Media.builder().id(mediaId).status(MediaStatus.uploading).build();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(mediaRepository.findByIdInAndDeletedAtIsNull(Set.of(mediaId)))
        .thenReturn(List.of(uploading));

    assertThatThrownBy(() -> postService.attachMedia(userId, false, postId, Set.of(mediaId)))
        .isInstanceOf(ValidationException.class);

    verify(postRepository, never()).saveAndFlush(existing);
  }

  @Test
  void attachMedia_rejectsUnknownMedia() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.created);
    UUID mediaId = UUID.randomUUID();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(mediaRepository.findByIdInAndDeletedAtIsNull(Set.of(mediaId))).thenReturn(List.of());

    assertThatThrownBy(() -> postService.attachMedia(userId, false, postId, Set.of(mediaId)))
        .isInstanceOf(ValidationException.class);

    verify(postRepository, never()).saveAndFlush(existing);
  }

  @Test
  void attachMedia_nonOwnerNonAdminGetsNotFound() {
    UUID postId = UUID.randomUUID();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.attachMedia(userId, false, postId, Set.of()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }

  @Test
  void attachRecipes_replacesWholeSetDetachingPreviouslyAttached() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.created);
    UUID keptId = UUID.randomUUID();
    UUID droppedId = UUID.randomUUID();
    Recipe kept =
        Recipe.builder().id(keptId).posts(new java.util.HashSet<>(Set.of(existing))).build();
    Recipe dropped =
        Recipe.builder().id(droppedId).posts(new java.util.HashSet<>(Set.of(existing))).build();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(recipeRepository.findByIdInAndDeletedAtIsNull(Set.of(keptId))).thenReturn(List.of(kept));
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(postId))
        .thenReturn(List.of(kept, dropped));
    when(postMapper.toListResponse(existing, Set.of(keptId)))
        .thenReturn(PostListResponse.builder().id(postId).build());

    postService.attachRecipes(userId, false, postId, Set.of(keptId));

    assertThat(kept.getPosts()).contains(existing);
    assertThat(dropped.getPosts()).doesNotContain(existing);
    verify(recipeRepository).saveAll(List.of(kept, dropped));
    verify(recipeRepository).saveAll(List.of(kept));
  }

  @Test
  void attachRecipes_emptySetDetachesAll() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.created);
    Recipe attached =
        Recipe.builder()
            .id(UUID.randomUUID())
            .posts(new java.util.HashSet<>(Set.of(existing)))
            .build();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(postId)).thenReturn(List.of(attached));
    when(postMapper.toListResponse(existing, Set.of()))
        .thenReturn(PostListResponse.builder().id(postId).build());

    postService.attachRecipes(userId, false, postId, Set.of());

    assertThat(attached.getPosts()).doesNotContain(existing);
  }

  @Test
  void attachRecipes_rejectsUnknownOrDeletedRecipe() {
    UUID postId = UUID.randomUUID();
    Post existing = ownedPost(postId, PostStatus.created);
    UUID recipeId = UUID.randomUUID();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existing));
    when(recipeRepository.findByIdInAndDeletedAtIsNull(Set.of(recipeId))).thenReturn(List.of());

    assertThatThrownBy(() -> postService.attachRecipes(userId, false, postId, Set.of(recipeId)))
        .isInstanceOf(ValidationException.class);

    verify(recipeRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void attachRecipes_nonOwnerNonAdminGetsNotFound() {
    UUID postId = UUID.randomUUID();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.attachRecipes(userId, false, postId, Set.of()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }
}
