package com.vegalife.service.profile;

import com.vegalife.dto.mapper.profile.ProfileMapper;
import com.vegalife.dto.request.profile.UpdateProfileRequest;
import com.vegalife.dto.response.profile.ProfileResponse;
import com.vegalife.model.user.User;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

  private final UserProfileRepository profileRepository;
  private final UserRepository userRepository;
  private final ProfileMapper profileMapper;

  @Transactional
  public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
    if (!request.hasAnyField()) {
      throw new ValidationException("At least one profile field must be provided");
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    UserProfile profile =
        profileRepository
            .findByUserId(userId)
            .orElseGet(
                () -> {
                  log.info("Creating new profile for user: {}", userId);
                  UserProfile newProfile = profileMapper.toEntity(request);
                  newProfile.setUser(user);
                  return newProfile;
                });

    profile = profileMapper.updateEntityFromRequest(request, profile);
    profile = profileRepository.save(profile);

    log.info("Profile updated for user: {}", userId);

    return profileMapper.toResponse(profile);
  }

  @Transactional(readOnly = true)
  public ProfileResponse getProfile(UUID userId) {
    UserProfile profile =
        profileRepository
            .findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

    return profileMapper.toResponse(profile);
  }
}
