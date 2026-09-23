package com.vegalife.unit.controller.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.vegalife.controller.profile.ProfileController;
import com.vegalife.dto.request.profile.UpdateProfileRequest;
import com.vegalife.dto.response.profile.ProfileResponse;
import com.vegalife.service.profile.UserProfileService;
import com.vegalife.shared.dto.ApiResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

  @Mock private UserProfileService profileService;

  @InjectMocks private ProfileController profileController;

  private UUID userId;
  private UpdateProfileRequest request;
  private ProfileResponse profileResponse;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    request =
        UpdateProfileRequest.builder()
            .heightCm(new BigDecimal("175.5"))
            .weightKg(new BigDecimal("70.2"))
            .age(25)
            .gender("male")
            .description("Test description")
            .avatarUrl("https://example.com/avatar.jpg")
            .build();

    profileResponse =
        ProfileResponse.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .heightCm(new BigDecimal("175.5"))
            .weightKg(new BigDecimal("70.2"))
            .age(25)
            .gender("male")
            .description("Test description")
            .avatarUrl("https://example.com/avatar.jpg")
            .build();
  }

  @Test
  void updateProfile_shouldReturnSuccessResponse() {
    when(profileService.updateProfile(userId, request)).thenReturn(profileResponse);

    ResponseEntity<ApiResponse<ProfileResponse>> response =
        profileController.updateProfile(userId, request);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
    assertThat(response.getBody().getMessage()).isEqualTo("Profile updated successfully");
    assertThat(response.getBody().getData()).isEqualTo(profileResponse);

    verify(profileService).updateProfile(userId, request);
  }
}
