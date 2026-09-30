package com.vegalife.unit.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.media.MediaMapper;
import com.vegalife.dto.request.media.MediaUploadRequest;
import com.vegalife.dto.response.media.MediaResponse;
import com.vegalife.dto.response.media.MediaUploadGrantResponse;
import com.vegalife.model.post.Media;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.media.MediaService;
import com.vegalife.service.media.PresignedUploadProvider;
import com.vegalife.service.media.UploadGrant;
import com.vegalife.service.media.VerifiedUpload;
import com.vegalife.shared.config.MediaProperties;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

  private static final int IMAGE_CEILING = 5 * 1024 * 1024;
  private static final int VIDEO_CEILING = 50 * 1024 * 1024;
  private static final String SEED_URL = "https://cdn.fake.test/media/seeded.png";

  @Mock private MediaRepository mediaRepository;

  @Mock private UserRepository userRepository;

  @Mock private PresignedUploadProvider uploadProvider;

  @Mock private MediaMapper mediaMapper;

  private MediaProperties mediaProperties;
  private MediaService mediaService;

  private UUID userId;
  private UUID mediaId;
  private User user;
  private Instant createdAt;

  @BeforeEach
  void setUp() {
    mediaProperties = new MediaProperties();
    mediaProperties.getCloudinary().setCloudName("testcloud");
    mediaService =
        new MediaService(
            mediaRepository, userRepository, uploadProvider, mediaProperties, mediaMapper);
    userId = UUID.randomUUID();
    mediaId = UUID.randomUUID();
    user = User.builder().id(userId).username("mediaowner").build();
    createdAt = Instant.now();
  }

  @Test
  void createGrant_persistsRowAndReturnsServerDerivedPublicId() {
    String expectedPublicId = "testcloud/" + userId + "/" + mediaId;
    stubGrantCreation(expectedPublicId);

    MediaUploadGrantResponse response =
        mediaService.createGrant(userId, uploadRequest("image/jpeg", 1024L));

    assertThat(response.getMediaId()).isEqualTo(mediaId);
    assertThat(response.getStatus()).isEqualTo("uploading");
    assertThat(response.getExpiresAt())
        .isEqualTo(createdAt.plus(mediaProperties.getUpload().getGrantTtl()));
    assertThat(response.getUpload().getMethod()).isEqualTo("POST");
    assertThat(response.getUpload().getUrl()).isEqualTo("https://upload.fake.test/v1/signed");
    assertThat(response.getUpload().getFields().getPublicId()).isEqualTo(expectedPublicId);
    assertThat(response.getUpload().getFields().getApiKey()).isEqualTo("fake-api-key");
    assertThat(response.getUpload().getFields().getMaxFileSize()).isEqualTo(IMAGE_CEILING);
    assertThat(response.getUpload().getFields().getAllowedFormats())
        .containsExactly("jpg", "png", "webp");
    verify(userRepository).findById(userId);
    verify(uploadProvider).prepare(expectedPublicId, "image/jpeg");
    verify(mediaRepository, times(2)).saveAndFlush(any(Media.class));
  }

  @Test
  void createGrant_unsupportedContentType_rejectsBeforeAnyInteraction() {
    MediaUploadRequest request = uploadRequest("image/gif", 1024L);

    assertThatThrownBy(() -> mediaService.createGrant(userId, request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Unsupported content type: image/gif");

    verifyNoInteractions(userRepository, mediaRepository, uploadProvider);
  }

  @Test
  void createGrant_imageOverCeiling_rejectsBeforeAnyInteraction() {
    MediaUploadRequest request = uploadRequest("image/jpeg", IMAGE_CEILING + 1L);

    assertThatThrownBy(() -> mediaService.createGrant(userId, request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("File exceeds the 5 MB image limit");

    verifyNoInteractions(userRepository, mediaRepository, uploadProvider);
  }

  @Test
  void createGrant_videoOverCeiling_rejectsBeforeAnyInteraction() {
    MediaUploadRequest request = uploadRequest("video/mp4", VIDEO_CEILING + 1L);

    assertThatThrownBy(() -> mediaService.createGrant(userId, request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("File exceeds the 50 MB limit");

    verifyNoInteractions(userRepository, mediaRepository, uploadProvider);
  }

  @Test
  void createGrant_sizeAtCeiling_isAccepted() {
    String expectedPublicId = "testcloud/" + userId + "/" + mediaId;
    stubGrantCreation(expectedPublicId);

    MediaUploadGrantResponse response =
        mediaService.createGrant(userId, uploadRequest("image/jpeg", (long) IMAGE_CEILING));

    assertThat(response.getStatus()).isEqualTo("uploading");
    verify(uploadProvider).prepare(expectedPublicId, "image/jpeg");
  }

  @Test
  void createGrant_unknownUser_throwsNotFound() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mediaService.createGrant(userId, uploadRequest("image/jpeg", 1024L)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");

    verifyNoInteractions(mediaRepository, uploadProvider);
  }

  @Test
  void confirm_seededObject_persistsProviderFactsAndMapsResponse() {
    Media row = uploadingRow();
    when(mediaRepository.findByIdAndDeletedAtIsNull(mediaId)).thenReturn(Optional.of(row));
    when(uploadProvider.verify(row.getExternalId(), "image/jpeg"))
        .thenReturn(Optional.of(new VerifiedUpload(SEED_URL, "image/jpeg", 2048L, 800, 600, null)));
    when(mediaRepository.saveAndFlush(row)).thenReturn(row);
    MediaResponse expected = MediaResponse.builder().mediaId(mediaId).status("succeed").build();
    when(mediaMapper.toResponse(row)).thenReturn(expected);

    MediaResponse response = mediaService.confirm(mediaId);

    assertThat(response).isSameAs(expected);
    assertThat(row.getStatus()).isEqualTo(Media.Status.succeed);
    assertThat(row.getMediaUrl()).isEqualTo(SEED_URL);
    assertThat(row.getMimeType()).isEqualTo("image/jpeg");
    assertThat(row.getFileSizeBytes()).isEqualTo(2048L);
    assertThat(row.getWidth()).isEqualTo(800);
    assertThat(row.getHeight()).isEqualTo(600);
    assertThat(row.getDurationSeconds()).isNull();
    verify(mediaMapper).toResponse(row);
  }

  @Test
  void confirm_missingObject_leavesRowUnconfirmed() {
    Media row = uploadingRow();
    when(mediaRepository.findByIdAndDeletedAtIsNull(mediaId)).thenReturn(Optional.of(row));
    when(uploadProvider.verify(row.getExternalId(), "image/jpeg")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mediaService.confirm(mediaId))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Upload verification failed");

    assertThat(row.getStatus()).isEqualTo(Media.Status.uploading);
    verify(mediaRepository, never()).saveAndFlush(any(Media.class));
  }

  @Test
  void confirm_expiredGrant_rejectsWithoutProviderCall() {
    Media row = uploadingRow();
    row.setCreatedAt(createdAt.minus(mediaProperties.getUpload().getGrantTtl()).minusSeconds(60));
    when(mediaRepository.findByIdAndDeletedAtIsNull(mediaId)).thenReturn(Optional.of(row));

    assertThatThrownBy(() -> mediaService.confirm(mediaId))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Upload grant has expired");

    verifyNoInteractions(uploadProvider);
    verify(mediaRepository, never()).saveAndFlush(any(Media.class));
  }

  @Test
  void confirm_alreadySucceed_throwsDuplicate() {
    Media row = uploadingRow();
    row.setStatus(Media.Status.succeed);
    when(mediaRepository.findByIdAndDeletedAtIsNull(mediaId)).thenReturn(Optional.of(row));

    assertThatThrownBy(() -> mediaService.confirm(mediaId))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Media upload has already been confirmed");

    verifyNoInteractions(uploadProvider);
    verify(mediaRepository, never()).saveAndFlush(any(Media.class));
  }

  @Test
  void confirm_failedRow_rejectsAsVerificationFailure() {
    Media row = uploadingRow();
    row.setStatus(Media.Status.failed);
    when(mediaRepository.findByIdAndDeletedAtIsNull(mediaId)).thenReturn(Optional.of(row));

    assertThatThrownBy(() -> mediaService.confirm(mediaId))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Upload verification failed");

    verifyNoInteractions(uploadProvider);
    verify(mediaRepository, never()).saveAndFlush(any(Media.class));
  }

  @Test
  void confirm_blankExternalId_rejectsAsVerificationFailure() {
    Media row = uploadingRow();
    row.setExternalId(" ");
    when(mediaRepository.findByIdAndDeletedAtIsNull(mediaId)).thenReturn(Optional.of(row));

    assertThatThrownBy(() -> mediaService.confirm(mediaId))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Upload verification failed");

    verifyNoInteractions(uploadProvider);
    verify(mediaRepository, never()).saveAndFlush(any(Media.class));
  }

  @Test
  void confirm_sizeOverCeiling_marksRowFailedAndThrows() {
    Media row = uploadingRow();
    when(mediaRepository.findByIdAndDeletedAtIsNull(mediaId)).thenReturn(Optional.of(row));
    when(uploadProvider.verify(row.getExternalId(), "image/jpeg"))
        .thenReturn(
            Optional.of(
                new VerifiedUpload(SEED_URL, "image/jpeg", IMAGE_CEILING + 1L, 800, 600, null)));
    when(mediaRepository.saveAndFlush(row)).thenReturn(row);

    assertThatThrownBy(() -> mediaService.confirm(mediaId))
        .isInstanceOf(ValidationException.class)
        .hasMessage("File exceeds the 5 MB image limit");

    assertThat(row.getStatus()).isEqualTo(Media.Status.failed);
    verify(mediaRepository).saveAndFlush(row);
  }

  @Test
  void confirm_unknownRow_throwsNotFound() {
    when(mediaRepository.findByIdAndDeletedAtIsNull(mediaId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mediaService.confirm(mediaId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Media not found");

    verifyNoInteractions(uploadProvider);
  }

  @Test
  void get_mapsRowThroughMapper() {
    Media row = uploadingRow();
    when(mediaRepository.findByIdAndDeletedAtIsNull(mediaId)).thenReturn(Optional.of(row));
    MediaResponse expected = MediaResponse.builder().mediaId(mediaId).status("uploading").build();
    when(mediaMapper.toResponse(row)).thenReturn(expected);

    assertThat(mediaService.get(mediaId)).isSameAs(expected);
    verify(mediaMapper).toResponse(row);
  }

  @Test
  void get_unknownRow_throwsNotFound() {
    when(mediaRepository.findByIdAndDeletedAtIsNull(mediaId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mediaService.get(mediaId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Media not found");
  }

  private void stubGrantCreation(String expectedPublicId) {
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(mediaRepository.saveAndFlush(any(Media.class)))
        .thenAnswer(
            invocation -> {
              Media media = invocation.getArgument(0);
              if (media.getId() == null) {
                media.setId(mediaId);
                media.setCreatedAt(createdAt);
              }
              return media;
            });
    when(uploadProvider.prepare(expectedPublicId, "image/jpeg"))
        .thenReturn(
            new UploadGrant(
                "https://upload.fake.test/v1/signed",
                expectedPublicId,
                "fake-api-key",
                1234L,
                "fake-signature",
                IMAGE_CEILING,
                List.of("jpg", "png", "webp")));
  }

  private Media uploadingRow() {
    return Media.builder()
        .id(mediaId)
        .status(Media.Status.uploading)
        .mimeType("image/jpeg")
        .externalId("testcloud/" + userId + "/" + mediaId)
        .uploadedBy(user)
        .createdAt(createdAt)
        .build();
  }

  private MediaUploadRequest uploadRequest(String contentType, Long sizeBytes) {
    return MediaUploadRequest.builder()
        .contentType(contentType)
        .fileName("photo.jpg")
        .sizeBytes(sizeBytes)
        .build();
  }
}
