package com.vegalife.controller.profile;

import com.vegalife.dto.request.profile.UpdateProfileRequest;
import com.vegalife.dto.response.profile.ProfileResponse;
import com.vegalife.service.profile.UserProfileService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ProfileController {

  private final UserProfileService profileService;

  @GetMapping
  public ResponseEntity<ApiResponse<ProfileResponse>> getOwnProfile(
      @AuthenticationPrincipal UUID userId) {

    ProfileResponse response = profileService.getProfile(userId);
    return ResponseEntity.ok(ApiResponse.success(response, "Profile retrieved successfully"));
  }

  @GetMapping("/{userId}")
  public ResponseEntity<ApiResponse<ProfileResponse>> getProfileByUserId(
      @PathVariable UUID userId) {

    ProfileResponse response = profileService.getProfile(userId);
    return ResponseEntity.ok(ApiResponse.success(response, "Profile retrieved successfully"));
  }

  @PutMapping
  public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody UpdateProfileRequest request) {

    ProfileResponse response = profileService.updateProfile(userId, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Profile updated successfully"));
  }
}
