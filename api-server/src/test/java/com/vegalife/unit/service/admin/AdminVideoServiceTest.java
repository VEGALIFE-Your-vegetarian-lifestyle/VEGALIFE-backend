package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.admin.AdminVideoMapper;
import com.vegalife.dto.request.admin.VideoListRequest;
import com.vegalife.dto.response.admin.AdminVideoListResponse;
import com.vegalife.dto.response.admin.AdminVideoPostResponse;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.MediaPostRow;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.service.admin.AdminVideoService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class AdminVideoServiceTest {

  @Mock private MediaRepository mediaRepository;

  @Mock private PostRepository postRepository;

  @Mock private AdminVideoMapper adminVideoMapper;

  @InjectMocks private AdminVideoService adminVideoService;

  private User uploader;
  private Media video;
  private Post post;
  private AdminVideoListResponse videoResponse;
  private AdminVideoPostResponse postResponse;
  private Pageable expectedPageable;

  @BeforeEach
  void setUp() {
    uploader =
        User.builder()
            .id(UUID.randomUUID())
            .username("janedoe")
            .email("jane@example.com")
            .passwordHash("$2a$10$secret")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    video =
        Media.builder()
            .id(UUID.randomUUID())
            .mediaUrl("https://cdn.example.com/cooking.mp4")
            .thumbnailUrl("https://cdn.example.com/cooking.jpg")
            .description("Stir fry tutorial")
            .status(Media.Status.succeed)
            .durationSeconds(300)
            .fileSizeBytes(1024L)
            .mimeType("video/mp4")
            .width(1920)
            .height(1080)
            .uploadedBy(uploader)
            .externalId("ext-1")
            .createdAt(Instant.parse("2026-09-21T10:00:00Z"))
            .updatedAt(Instant.parse("2026-09-21T11:00:00Z"))
            .build();
    post =
        Post.builder()
            .id(UUID.randomUUID())
            .user(uploader)
            .title("Vegan chili")
            .content("A hearty chili recipe")
            .status(Post.Status.published)
            .flag(Post.Flag.PASSED)
            .viewCount(7)
            .createdAt(Instant.parse("2026-09-21T10:00:00Z"))
            .build();
    videoResponse =
        AdminVideoListResponse.builder()
            .id(video.getId())
            .mediaUrl(video.getMediaUrl())
            .thumbnailUrl(video.getThumbnailUrl())
            .description(video.getDescription())
            .status("succeed")
            .durationSeconds(300)
            .fileSizeBytes(1024L)
            .mimeType("video/mp4")
            .width(1920)
            .height(1080)
            .externalId("ext-1")
            .createdAt(video.getCreatedAt())
            .updatedAt(video.getUpdatedAt())
            .userId(uploader.getId())
            .username("janedoe")
            .email("jane@example.com")
            .build();
    postResponse =
        AdminVideoPostResponse.builder()
            .id(post.getId())
            .title("Vegan chili")
            .status("published")
            .build();
    expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  void listVideos_withDefaults_usesDefaultPaginationAndSort() {
    Page<Media> page = new PageImpl<>(List.of(video), expectedPageable, 1);
    when(mediaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    when(adminVideoMapper.toResponse(video)).thenReturn(videoResponse);
    when(postRepository.findPostRowsByMediaIdsIn(List.of(video.getId())))
        .thenReturn(List.of(new MediaPostRow(video.getId(), post)));
    when(adminVideoMapper.toPostResponse(post)).thenReturn(postResponse);

    PageResponse<AdminVideoListResponse> result =
        adminVideoService.listVideos(new VideoListRequest());

    assertThat(result.getContent()).containsExactly(videoResponse);
    assertThat(videoResponse.getPosts()).containsExactly(postResponse);
    assertThat(result.getPage()).isZero();
    assertThat(result.getSize()).isEqualTo(20);
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getTotalPages()).isEqualTo(1);
    assertThat(result.isFirst()).isTrue();
    assertThat(result.isLast()).isTrue();

    verify(mediaRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void listVideos_withFilters_parsesFiltersAndSort() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-12-31T23:59:59Z");
    VideoListRequest request =
        VideoListRequest.builder()
            .page(1)
            .size(10)
            .sort("durationSeconds,asc")
            .status("succeed")
            .userId(uploader.getId())
            .createdFrom(from)
            .createdTo(to)
            .build();
    Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.ASC, "durationSeconds"));
    Page<Media> page = new PageImpl<>(List.of(video), pageable, 1);
    when(mediaRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
    when(adminVideoMapper.toResponse(video)).thenReturn(videoResponse);
    when(postRepository.findPostRowsByMediaIdsIn(List.of(video.getId())))
        .thenReturn(List.of(new MediaPostRow(video.getId(), post)));
    when(adminVideoMapper.toPostResponse(post)).thenReturn(postResponse);

    PageResponse<AdminVideoListResponse> result = adminVideoService.listVideos(request);

    assertThat(result.getContent()).containsExactly(videoResponse);
    assertThat(videoResponse.getPosts()).containsExactly(postResponse);
    assertThat(result.getPage()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(10);
    verify(mediaRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  void listVideos_whenCreatedFromAfterCreatedTo_throwsValidationException() {
    VideoListRequest request =
        VideoListRequest.builder()
            .createdFrom(Instant.parse("2026-12-01T00:00:00Z"))
            .createdTo(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    assertThatThrownBy(() -> adminVideoService.listVideos(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("createdFrom must be before createdTo");
  }

  @Test
  void listVideos_withInvalidStatus_throwsValidationException() {
    VideoListRequest request = VideoListRequest.builder().status("bogus").build();

    assertThatThrownBy(() -> adminVideoService.listVideos(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Status must be one of: uploading, succeed, failed");
  }

  @Test
  void listVideos_withMalformedSort_throwsValidationException() {
    VideoListRequest request = VideoListRequest.builder().sort("createdAt,asc,extra").build();

    assertThatThrownBy(() -> adminVideoService.listVideos(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Sort must be in the form property,asc|desc");
  }

  @Test
  void listVideos_withDisallowedSortProperty_throwsValidationException() {
    VideoListRequest request = VideoListRequest.builder().sort("mediaUrl,asc").build();

    assertThatThrownBy(() -> adminVideoService.listVideos(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage(
            "Sort property must be one of: createdAt, updatedAt, durationSeconds, fileSizeBytes");
  }

  @Test
  void listVideos_withEmptyPage_returnsEmptyContentWithoutPostLookup() {
    Page<Media> page = Page.empty(expectedPageable);
    when(mediaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

    PageResponse<AdminVideoListResponse> result =
        adminVideoService.listVideos(new VideoListRequest());

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
    assertThat(result.isFirst()).isTrue();
    assertThat(result.isLast()).isTrue();

    verify(postRepository, never()).findPostRowsByMediaIdsIn(anyList());
  }

  @Test
  void listVideos_whenVideoHasNoPosts_attachesEmptyPostsList() {
    Page<Media> page = new PageImpl<>(List.of(video), expectedPageable, 1);
    when(mediaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    when(adminVideoMapper.toResponse(video)).thenReturn(videoResponse);
    when(postRepository.findPostRowsByMediaIdsIn(List.of(video.getId()))).thenReturn(List.of());

    PageResponse<AdminVideoListResponse> result =
        adminVideoService.listVideos(new VideoListRequest());

    assertThat(result.getContent()).hasSize(1);
    assertThat(videoResponse.getPosts()).isEmpty();
  }
}
