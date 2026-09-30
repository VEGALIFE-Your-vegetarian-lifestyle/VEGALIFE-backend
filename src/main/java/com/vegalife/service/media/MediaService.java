package com.vegalife.service.media;

import com.vegalife.dto.mapper.media.MediaMapper;
import com.vegalife.dto.request.media.MediaUploadRequest;
import com.vegalife.dto.response.media.MediaResponse;
import com.vegalife.dto.response.media.MediaUploadGrantResponse;
import com.vegalife.model.post.Media;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.shared.config.MediaProperties;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Media upload lifecycle (spec {@code docs/feats/upload-media-api.md}): mint a direct-to-storage
 * grant, then confirm the object against what the provider actually holds. All policy lives here —
 * the provider only signs and reads back.
 */
@Service
@RequiredArgsConstructor
public class MediaService {

  private final MediaRepository mediaRepository;
  private final UserRepository userRepository;
  private final PresignedUploadProvider uploadProvider;
  private final MediaProperties mediaProperties;
  private final MediaMapper mediaMapper;

  /**
   * FR-001..FR-006: validates the declared content type and size, records the row owned by the
   * caller, and returns a grant bound to a server-chosen object key. Rolls back if the provider
   * cannot be reached, so no row outlives a failed grant.
   */
  @Transactional
  public MediaUploadGrantResponse createGrant(UUID userId, MediaUploadRequest request) {
    String contentType = request.getContentType();
    if (!mediaProperties.getUpload().allowedTypes().contains(contentType)) {
      throw new ValidationException("Unsupported content type: " + contentType);
    }
    long ceiling = mediaProperties.getUpload().maxBytesFor(contentType);
    if (request.getSizeBytes() != null && request.getSizeBytes() > ceiling) {
      throw new ValidationException(sizeLimitMessage(contentType));
    }

    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Media media =
        Media.builder()
            .status(Media.Status.uploading)
            .mimeType(contentType)
            .uploadedBy(user)
            .build();
    Media granted = mediaRepository.saveAndFlush(media);

    UploadGrant grant = uploadProvider.prepare(publicIdFor(userId, granted.getId()), contentType);
    granted.setExternalId(grant.publicId());
    Media saved = mediaRepository.saveAndFlush(granted);

    Instant expiresAt = saved.getCreatedAt().plus(mediaProperties.getUpload().getGrantTtl());
    return toGrantResponse(saved, grant, expiresAt);
  }

  /**
   * FR-007..FR-012: re-reads the object from the provider and persists what it reports. A missing
   * object, an expired grant, or a size above the class ceiling all leave the row unconfirmed.
   *
   * <p>{@code noRollbackFor} is required: the over-size branch (FR-010) writes {@code status =
   * failed} and then reports 400, and that write must survive the {@link ValidationException} that
   * carries the error out. Every other branch of this method writes nothing before it throws.
   */
  @Transactional(noRollbackFor = ValidationException.class)
  public MediaResponse confirm(UUID mediaId) {
    Media media = find(mediaId);

    if (media.getStatus() == Media.Status.succeed) {
      throw new DuplicateResourceException("Media upload has already been confirmed");
    }
    if (media.getStatus() == Media.Status.failed) {
      throw new ValidationException("Upload verification failed");
    }
    if (media.getExternalId() == null || media.getExternalId().isBlank()) {
      throw new ValidationException("Upload verification failed");
    }

    String contentType = media.getMimeType();
    if (media.getCreatedAt() == null
        || media
            .getCreatedAt()
            .plus(mediaProperties.getUpload().getGrantTtl())
            .isBefore(Instant.now())) {
      throw new ValidationException("Upload grant has expired");
    }

    VerifiedUpload upload =
        uploadProvider
            .verify(media.getExternalId(), contentType)
            .orElseThrow(() -> new ValidationException("Upload verification failed"));

    if (upload.fileSizeBytes() > mediaProperties.getUpload().maxBytesFor(contentType)) {
      media.setStatus(Media.Status.failed);
      mediaRepository.saveAndFlush(media);
      throw new ValidationException(sizeLimitMessage(contentType));
    }

    media.setStatus(Media.Status.succeed);
    media.setMediaUrl(upload.mediaUrl());
    media.setMimeType(upload.mimeType());
    media.setFileSizeBytes(upload.fileSizeBytes());
    media.setWidth(upload.width());
    media.setHeight(upload.height());
    media.setDurationSeconds(upload.durationSeconds());

    return mediaMapper.toResponse(mediaRepository.saveAndFlush(media));
  }

  /** Read endpoint: a row still in {@code uploading} simply reports null measurements. */
  @Transactional(readOnly = true)
  public MediaResponse get(UUID mediaId) {
    return mediaMapper.toResponse(find(mediaId));
  }

  private Media find(UUID mediaId) {
    return mediaRepository
        .findByIdAndDeletedAtIsNull(mediaId)
        .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
  }

  /** FR-005: the object key is derived from the cloud, the owner, and the media row. */
  private String publicIdFor(UUID userId, UUID mediaId) {
    return mediaProperties.getCloudinary().getCloudName() + "/" + userId + "/" + mediaId;
  }

  /** FR-014: the ceiling comes from configuration; this file only renders it as whole megabytes. */
  private String sizeLimitMessage(String contentType) {
    long megabytes =
        Math.max(
            1,
            (long)
                Math.ceil(mediaProperties.getUpload().maxBytesFor(contentType) / 1024.0 / 1024.0));
    return mediaProperties.getUpload().isImage(contentType)
        ? String.format("File exceeds the %d MB image limit", megabytes)
        : String.format("File exceeds the %d MB limit", megabytes);
  }

  private MediaUploadGrantResponse toGrantResponse(
      Media media, UploadGrant grant, Instant expiresAt) {
    MediaUploadGrantResponse.UploadFields fields =
        MediaUploadGrantResponse.UploadFields.builder()
            .apiKey(grant.apiKey())
            .timestamp(grant.timestamp())
            .signature(grant.signature())
            .publicId(grant.publicId())
            .maxFileSize(grant.maxFileSize())
            .allowedFormats(grant.allowedFormats())
            .build();
    MediaUploadGrantResponse.UploadInstruction instruction =
        MediaUploadGrantResponse.UploadInstruction.builder()
            .method("POST")
            .url(grant.uploadUrl())
            .headers(Map.of())
            .fields(fields)
            .build();
    return MediaUploadGrantResponse.builder()
        .mediaId(media.getId())
        .status(media.getStatus().name())
        .expiresAt(expiresAt)
        .upload(instruction)
        .build();
  }
}
