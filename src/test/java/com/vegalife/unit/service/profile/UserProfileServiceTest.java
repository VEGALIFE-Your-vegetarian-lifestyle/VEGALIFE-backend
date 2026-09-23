package com.vegalife.unit.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.vegalife.dto.mapper.profile.ProfileMapper;
import com.vegalife.dto.request.profile.UpdateProfileRequest;
import com.vegalife.dto.response.profile.ProfileResponse;
import com.vegalife.model.user.User;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.profile.UserProfileService;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

  @Mock private UserProfileRepository profileRepository;

  @Mock private UserRepository userRepository;

  @Mock private ProfileMapper profileMapper;

  @InjectMocks private UserProfileService profileService;

  private UUID userId;
  private User user;
  private UserProfile existingProfile;
  private UpdateProfileRequest request;
  private ProfileResponse expectedResponse;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    user = User.builder().id(userId).username("testuser").build();
    existingProfile =
        UserProfile.builder()
            .id(UUID.randomUUID())
            .user(user)
            .heightCm(new BigDecimal("170.0"))
            .weightKg(new BigDecimal("65.0"))
            .age(25)
            .gender(UserProfile.Gender.male)
            .description("Old description")
            .avatarUrl("https://old-avatar.com")
            .build();

    request =
        UpdateProfileRequest.builder()
            .heightCm(new BigDecimal("175.5"))
            .weightKg(new BigDecimal("70.2"))
            .age(26)
            .gender("female")
            .description("New description")
            .avatarUrl("https://new-avatar.com")
            .build();

    expectedResponse =
        ProfileResponse.builder()
            .id(existingProfile.getId())
            .userId(userId)
            .heightCm(new BigDecimal("175.5"))
            .weightKg(new BigDecimal("70.2"))
            .age(26)
            .gender("female")
            .description("New description")
            .avatarUrl("https://new-avatar.com")
            .build();
  }

  @Test
  void updateProfile_whenProfileExists_shouldUpdateAndReturnResponse() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existingProfile));
    when(profileMapper.updateEntityFromRequest(request, existingProfile))
        .thenReturn(existingProfile);
    when(profileRepository.save(existingProfile)).thenReturn(existingProfile);
    when(profileMapper.toResponse(existingProfile)).thenReturn(expectedResponse);

    ProfileResponse result = profileService.updateProfile(userId, request);

    assertThat(result).isEqualTo(expectedResponse);
    verify(profileRepository).save(existingProfile);
    verify(profileMapper).updateEntityFromRequest(request, existingProfile);
  }

  @Test
  void updateProfile_whenProfileNotExists_shouldCreateNewProfile() {
    UserProfile newProfile = UserProfile.builder().user(user).build();
    UserProfile updatedProfile = UserProfile.builder().user(user).build();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(profileMapper.toEntity(request)).thenReturn(newProfile);
    when(profileMapper.updateEntityFromRequest(request, newProfile)).thenReturn(updatedProfile);
    when(profileRepository.save(updatedProfile)).thenReturn(updatedProfile);
    when(profileMapper.toResponse(updatedProfile)).thenReturn(expectedResponse);

    ProfileResponse result = profileService.updateProfile(userId, request);

    assertThat(result).isEqualTo(expectedResponse);
    verify(profileMapper).toEntity(request);
    verify(profileMapper).updateEntityFromRequest(request, newProfile);
    verify(profileRepository).save(updatedProfile);
  }

  @Test
  void updateProfile_whenNoFieldsProvided_shouldThrowValidationException() {
    UpdateProfileRequest emptyRequest = UpdateProfileRequest.builder().build();

    assertThatThrownBy(() -> profileService.updateProfile(userId, emptyRequest))
        .isInstanceOf(ValidationException.class)
        .hasMessage("At least one profile field must be provided");

    verifyNoInteractions(userRepository, profileRepository, profileMapper);
  }

  @Test
  void updateProfile_whenUserNotFound_shouldThrowResourceNotFoundException() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> profileService.updateProfile(userId, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");

    verify(profileRepository, never()).findByUserId(any());
  }

  @Test
  void updateProfile_partialUpdate_shouldOnlyUpdateProvidedFields() {
    UpdateProfileRequest partialRequest =
        UpdateProfileRequest.builder().heightCm(new BigDecimal("180.0")).build();

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existingProfile));
    when(profileMapper.updateEntityFromRequest(partialRequest, existingProfile))
        .thenReturn(existingProfile);
    when(profileRepository.save(existingProfile)).thenReturn(existingProfile);
    when(profileMapper.toResponse(existingProfile)).thenReturn(expectedResponse);

    ProfileResponse result = profileService.updateProfile(userId, partialRequest);

    assertThat(result).isEqualTo(expectedResponse);
    verify(profileMapper).updateEntityFromRequest(partialRequest, existingProfile);
  }
}
