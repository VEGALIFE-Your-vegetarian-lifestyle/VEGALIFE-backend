package com.vegalife.controller.profile;

import com.vegalife.dto.request.profile.UpdateProfileRequest;
import com.vegalife.dto.response.profile.ProfileResponse;
import com.vegalife.service.profile.UserProfileService;
import com.vegalife.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

  private final UserProfileService profileService;

  @PutMapping
  public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody UpdateProfileRequest request) {

    ProfileResponse response = profileService.updateProfile(userId, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Profile updated successfully"));
  }
}
