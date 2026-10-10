package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.admin.AdminPostMapper;
import com.vegalife.dto.request.admin.PostListRequest;
import com.vegalife.dto.request.admin.PostModerationRequest;
import com.vegalife.dto.response.admin.AdminPostListResponse;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.service.admin.AdminPostService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AdminPostServiceTest {

  @Mock private PostRepository postRepository;

  @Mock private ModerationLogRepository moderationLogRepository;

  @Mock private AdminPostMapper adminPostMapper;

  @InjectMocks private AdminPostService adminPostService;

  private User author;
  private Post post;
  private AdminPostListResponse postResponse;
  private Pageable expectedPageable;

  @BeforeEach
  void setUp() {
    author =
        User.builder()
            .id(UUID.randomUUID())
            .username("janedoe")
            .email("jane@example.com")
            .passwordHash("$2a$10$secret")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    post =
        Post.builder()
            .id(UUID.randomUUID())
            .user(author)
            .title("Vegan chili")
            .content("A hearty chili recipe")
            .status(Post.Status.published)
            .flag(Post.Flag.PASSED)
            .viewCount(7)
            .createdAt(Instant.parse("2026-09-21T10:00:00Z"))
            .build();
    postResponse =
        AdminPostListResponse.builder()
            .id(post.getId())
            .title("Vegan chili")
            .content("A hearty chili recipe")
            .status("published")
            .flag("PASSED")
            .viewCount(7)
            .createdAt(post.getCreatedAt())
            .userId(author.getId())
            .username("janedoe")
            .email("jane@example.com")
            .build();
    expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  void listPosts_withDefaults_usesDefaultPaginationAndSort() {
    Page<Post> page = new PageImpl<>(List.of(post), expectedPageable, 1);
    when(postRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    when(adminPostMapper.toResponse(post)).thenReturn(postResponse);

    PageResponse<AdminPostListResponse> result = adminPostService.listPosts(new PostListRequest());

    assertThat(result.getContent()).containsExactly(postResponse);
    assertThat(result.getPage()).isZero();
    assertThat(result.getSize()).isEqualTo(20);
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getTotalPages()).isEqualTo(1);
    assertThat(result.isFirst()).isTrue();
    assertThat(result.isLast()).isTrue();

    verify(postRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void listPosts_withFilters_parsesFiltersAndSort() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-12-31T23:59:59Z");
    PostListRequest request =
        PostListRequest.builder()
            .page(1)
            .size(10)
            .sort("viewCount,asc")
            .status("unpublished")
            .flag("REJECTED")
            .userId(author.getId())
            .categoryId(UUID.randomUUID())
            .createdFrom(from)
            .createdTo(to)
            .build();
    Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.ASC, "viewCount"));
    Page<Post> page = new PageImpl<>(List.of(post), pageable, 1);
    when(postRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
    when(adminPostMapper.toResponse(post)).thenReturn(postResponse);

    PageResponse<AdminPostListResponse> result = adminPostService.listPosts(request);

    assertThat(result.getContent()).containsExactly(postResponse);
    assertThat(result.getPage()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(10);
    verify(postRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  void listPosts_whenCreatedFromAfterCreatedTo_throwsValidationException() {
    PostListRequest request =
        PostListRequest.builder()
            .createdFrom(Instant.parse("2026-12-01T00:00:00Z"))
            .createdTo(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    assertThatThrownBy(() -> adminPostService.listPosts(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("createdFrom must be before createdTo");
  }

  @Test
  void listPosts_withInvalidStatus_throwsValidationException() {
    PostListRequest request = PostListRequest.builder().status("bogus").build();

    assertThatThrownBy(() -> adminPostService.listPosts(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Status must be one of: created, processed, published, unpublished, hidden");
  }

  @Test
  void listPosts_withInvalidFlag_throwsValidationException() {
    PostListRequest request = PostListRequest.builder().flag("bogus").build();

    assertThatThrownBy(() -> adminPostService.listPosts(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Flag must be one of: all, PENDING, PASSED, REJECTED, NEEDS_REVIEW");
  }

  @Test
  void listPosts_withFlagAll_passesNoFlagFilter() {
    PostListRequest request = PostListRequest.builder().flag("all").build();
    Page<Post> page = new PageImpl<>(List.of(post), expectedPageable, 1);
    when(postRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    when(adminPostMapper.toResponse(post)).thenReturn(postResponse);

    PageResponse<AdminPostListResponse> result = adminPostService.listPosts(request);

    assertThat(result.getContent()).containsExactly(postResponse);
    verify(postRepository).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  void listPosts_withMalformedSort_throwsValidationException() {
    PostListRequest request = PostListRequest.builder().sort("createdAt,asc,extra").build();

    assertThatThrownBy(() -> adminPostService.listPosts(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Sort must be in the form property,asc|desc");
  }

  @Test
  void listPosts_withDisallowedSortProperty_throwsValidationException() {
    PostListRequest request = PostListRequest.builder().sort("passwordHash,asc").build();

    assertThatThrownBy(() -> adminPostService.listPosts(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage(
            "Sort property must be one of: createdAt, publishedAt, updatedAt, viewCount, title");
  }

  @Test
  void listPosts_withEmptyPage_returnsEmptyContent() {
    Page<Post> page = Page.empty(expectedPageable);
    when(postRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

    PageResponse<AdminPostListResponse> result = adminPostService.listPosts(new PostListRequest());

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
    assertThat(result.isFirst()).isTrue();
    assertThat(result.isLast()).isTrue();
  }

  @Test
  void moderatePost_publish_setsStatusAndLogsWithReason() {
    UUID adminId = UUID.randomUUID();
    post.setStatus(Post.Status.unpublished);
    post.setFlag(Post.Flag.REJECTED);
    post.setCategories(Set.of(Category.builder().id(UUID.randomUUID()).name("Dinner").build()));
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));
    when(postRepository.saveAndFlush(post)).thenReturn(post);
    when(adminPostMapper.toResponse(post)).thenReturn(postResponse);

    adminPostService.moderatePost(
        adminId,
        post.getId(),
        PostModerationRequest.builder()
            .action(PostModerationRequest.Action.PUBLISH)
            .reason("Vegetarian recipe, filter false positive")
            .build());

    assertThat(post.getStatus()).isEqualTo(Post.Status.published);
    assertThat(post.getPublishedAt()).isNotNull();
    assertThat(post.getFlag()).isEqualTo(Post.Flag.REJECTED);

    ArgumentCaptor<ModerationLog> captor = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository).save(captor.capture());
    ModerationLog entry = captor.getValue();
    assertThat(entry.getActorId()).isEqualTo(adminId);
    assertThat(entry.getAction()).isEqualTo("PUBLISH_POST");
    assertThat(entry.getTargetType()).isEqualTo("POST");
    assertThat(entry.getTargetId()).isEqualTo(post.getId());
    assertThat(entry.getReason()).isEqualTo("Vegetarian recipe, filter false positive");
  }

  @Test
  void moderatePost_unpublish_clearsPublishedAtAndLogs() {
    UUID adminId = UUID.randomUUID();
    post.setStatus(Post.Status.published);
    post.setPublishedAt(Instant.parse("2026-09-21T10:00:00Z"));
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));
    when(postRepository.saveAndFlush(post)).thenReturn(post);
    when(adminPostMapper.toResponse(post)).thenReturn(postResponse);

    adminPostService.moderatePost(
        adminId,
        post.getId(),
        PostModerationRequest.builder().action(PostModerationRequest.Action.UNPUBLISH).build());

    assertThat(post.getStatus()).isEqualTo(Post.Status.unpublished);
    assertThat(post.getPublishedAt()).isNull();

    ArgumentCaptor<ModerationLog> captor = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository).save(captor.capture());
    assertThat(captor.getValue().getAction()).isEqualTo("UNPUBLISH_POST");
    assertThat(captor.getValue().getReason()).isNull();
  }

  @Test
  void moderatePost_publishAlreadyPublished_isNoOpWithoutLog() {
    UUID adminId = UUID.randomUUID();
    post.setStatus(Post.Status.published);
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));
    when(adminPostMapper.toResponse(post)).thenReturn(postResponse);

    adminPostService.moderatePost(
        adminId,
        post.getId(),
        PostModerationRequest.builder().action(PostModerationRequest.Action.PUBLISH).build());

    verify(postRepository, never()).saveAndFlush(any(Post.class));
    verify(moderationLogRepository, never()).save(any(ModerationLog.class));
  }

  @Test
  void moderatePost_publishWithoutCategory_throwsValidationException() {
    UUID adminId = UUID.randomUUID();
    post.setStatus(Post.Status.unpublished);
    post.setCategories(Set.of());
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));

    assertThatThrownBy(
            () ->
                adminPostService.moderatePost(
                    adminId,
                    post.getId(),
                    PostModerationRequest.builder()
                        .action(PostModerationRequest.Action.PUBLISH)
                        .build()))
        .isInstanceOf(ValidationException.class)
        .hasMessage("At least one category is required to publish a post");

    verify(moderationLogRepository, never()).save(any(ModerationLog.class));
  }

  @Test
  void moderatePost_hiddenPost_throwsValidationException() {
    UUID adminId = UUID.randomUUID();
    post.setStatus(Post.Status.hidden);
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));

    assertThatThrownBy(
            () ->
                adminPostService.moderatePost(
                    adminId,
                    post.getId(),
                    PostModerationRequest.builder()
                        .action(PostModerationRequest.Action.PUBLISH)
                        .build()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void moderatePost_missingPost_throwsResourceNotFound() {
    UUID adminId = UUID.randomUUID();
    UUID postId = UUID.randomUUID();
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                adminPostService.moderatePost(
                    adminId,
                    postId,
                    PostModerationRequest.builder()
                        .action(PostModerationRequest.Action.UNPUBLISH)
                        .build()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }
}
