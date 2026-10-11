package com.vegalife.controller.media;

import com.vegalife.dto.request.media.MediaUploadRequest;
import com.vegalife.dto.response.media.MediaResponse;
import com.vegalife.dto.response.media.MediaUploadGrantResponse;
import com.vegalife.service.media.MediaService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Media upload endpoints ({@code docs/apis/media/}). Read endpoints require an authenticated caller
 * but enforce no ownership (media is attachable across users by product decision); DELETE is
 * owner-or-admin per BR-MEDIA-009.
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MediaController {

  private final MediaService mediaService;

  @PostMapping("/upload")
  public ResponseEntity<ApiResponse<MediaUploadGrantResponse>> createUploadGrant(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody MediaUploadRequest request) {
    MediaUploadGrantResponse grant = mediaService.createGrant(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(grant, "Upload grant created"));
  }

  @PostMapping("/{mediaId}/confirm")
  public ResponseEntity<ApiResponse<MediaResponse>> confirmUpload(@PathVariable UUID mediaId) {
    MediaResponse media = mediaService.confirm(mediaId);
    return ResponseEntity.ok(ApiResponse.success(media, "Media upload confirmed"));
  }

  @GetMapping("/{mediaId}")
  public ResponseEntity<ApiResponse<MediaResponse>> getMedia(@PathVariable UUID mediaId) {
    MediaResponse media = mediaService.get(mediaId);
    return ResponseEntity.ok(ApiResponse.success(media, "Media retrieved successfully"));
  }

  @DeleteMapping("/{mediaId}")
  public ResponseEntity<ApiResponse<Void>> deleteMedia(
      @AuthenticationPrincipal UUID userId,
      Authentication authentication,
      @PathVariable UUID mediaId) {
    mediaService.deleteMedia(userId, isAdmin(authentication), mediaId);
    return ResponseEntity.ok(ApiResponse.success(null, "Media deleted successfully"));
  }

  private boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }
}
