package com.vegalife.unit.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.post.PostMapper;
import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.request.post.PostUpdateRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.post.PostService;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

  @Mock private PostRepository postRepository;

  @Mock private UserRepository userRepository;

  @Mock private CategoryRepository categoryRepository;

  @Mock private MediaRepository mediaRepository;

  @Mock private PostMapper postMapper;

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
            .type(Post.Type.blog)
            .content("A simple plant-based lunch.")
            .featuredImageUrl("https://example.com/tofu-bowl.jpg")
            .build();
    post =
        Post.builder()
            .title(request.getTitle())
            .content(request.getContent())
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
    verify(postRepository).saveAndFlush(post);
  }

  @Test
  void createPost_publishesWhenAllRequirementsMet() {
    request.setPublish(true);
    request.setCategoryIds(Set.of(categoryId));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(categoryRepository.findByIdInAndDeletedAtIsNull(Set.of(categoryId)))
        .thenReturn(List.of(category));
    when(postMapper.toEntity(request)).thenReturn(post);
    when(postRepository.saveAndFlush(post)).thenReturn(post);

    postService.createPost(userId, request);

    assertThat(post.getStatus()).isEqualTo(Post.Status.published);
    assertThat(post.getPublishedAt()).isNotNull();
    assertThat(post.getCategories()).containsExactly(category);
  }

  @Test
  void createPost_rejectsPublishWithoutCategory() {
    request.setPublish(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> postService.createPost(userId, request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("At least one category is required to publish a post");

    verify(postRepository, never()).saveAndFlush(post);
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
  void createPost_rejectsBlogWithoutContent() {
    request.setContent(" ");
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> postService.createPost(userId, request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Content is required for a blog post");
  }

  @Test
  void createPost_rejectsVideoWithoutFileOrLink() {
    request.setType(Post.Type.video);
    request.setContent(null);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> postService.createPost(userId, request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("A video post requires a video file or link");
  }

  @Test
  void createPost_acceptsVideoWithUploadedMedia() {
    UUID mediaId = UUID.randomUUID();
    Media media = Media.builder().id(mediaId).status(Media.Status.succeed).build();
    request.setType(Post.Type.video);
    request.setContent(null);
    request.setMediaId(mediaId);
    post.setContent(null);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(mediaRepository.findByIdInAndDeletedAtIsNull(Set.of(mediaId))).thenReturn(List.of(media));
    when(postMapper.toEntity(request)).thenReturn(post);
    when(postRepository.saveAndFlush(post)).thenReturn(post);

    postService.createPost(userId, request);

    assertThat(post.getMedia()).containsExactly(media);
    assertThat(post.getContent()).isEmpty();
  }

  @Test
  void createPost_rejectsVideoWhoseUploadHasNotCompleted() {
    UUID mediaId = UUID.randomUUID();
    Media media = Media.builder().id(mediaId).status(Media.Status.uploading).build();
    request.setType(Post.Type.video);
    request.setMediaId(mediaId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(mediaRepository.findByIdInAndDeletedAtIsNull(Set.of(mediaId))).thenReturn(List.of(media));

    assertThatThrownBy(() -> postService.createPost(userId, request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Media upload has not completed");
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
            .featuredImageUrl("https://example.com/old.jpg")
            .status(Post.Status.published)
            .viewCount(12)
            .build();
    PostUpdateRequest updateRequest = PostUpdateRequest.builder().title("New title").build();
    PostListResponse expectedResponse = PostListResponse.builder().title("New title").build();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.of(existingPost));
    when(postRepository.saveAndFlush(existingPost)).thenReturn(existingPost);
    when(postMapper.toListResponse(existingPost)).thenReturn(expectedResponse);

    PostListResponse response = postService.updatePost(userId, postId, updateRequest);

    assertThat(response).isSameAs(expectedResponse);
    assertThat(existingPost.getTitle()).isEqualTo("New title");
    assertThat(existingPost.getContent()).isEqualTo("Keep this content");
    assertThat(existingPost.getFeaturedImageUrl()).isEqualTo("https://example.com/old.jpg");
    assertThat(existingPost.getUser()).isSameAs(user);
    assertThat(existingPost.getStatus()).isEqualTo(Post.Status.published);
    assertThat(existingPost.getViewCount()).isEqualTo(12);
    verify(postRepository).saveAndFlush(existingPost);
  }

  @Test
  void updatePost_throwsWhenPostDoesNotBelongToAuthenticatedUserOrIsDeleted() {
    UUID postId = UUID.randomUUID();
    PostUpdateRequest updateRequest = PostUpdateRequest.builder().title("New title").build();
    when(postRepository.findByIdAndUser_IdAndDeletedAtIsNull(postId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.updatePost(userId, postId, updateRequest))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");

    verify(postRepository, never()).saveAndFlush(post);
  }
}
