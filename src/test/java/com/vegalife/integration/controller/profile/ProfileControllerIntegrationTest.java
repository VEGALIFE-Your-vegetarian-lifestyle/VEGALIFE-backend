package com.vegalife.integration.controller.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vegalife.model.user.User;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private UserProfileRepository profileRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User testUser;
  private String accessToken;

  @BeforeEach
  void setUp() {
    testUser =
        User.builder()
            .username("profileuser")
            .email("profile@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    testUser = userRepository.save(testUser);

    accessToken = jwtTokenService.generateAccessToken(testUser);
  }

  @Test
  void updateProfile_withValidData_shouldReturn200AndUpdatedProfile() throws Exception {
    String requestJson =
        """
        {
          "height_cm": 175.5,
          "weight_kg": 70.2,
          "age": 25,
          "gender": "male",
          "description": "Vegan enthusiast",
          "avatar_url": "https://example.com/avatar.jpg"
        }
        """;

    mockMvc
        .perform(
            put("/api/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Profile updated successfully"))
        .andExpect(jsonPath("$.data.userId").value(testUser.getId().toString()))
        .andExpect(jsonPath("$.data.heightCm").value(175.5))
        .andExpect(jsonPath("$.data.weightKg").value(70.2))
        .andExpect(jsonPath("$.data.age").value(25))
        .andExpect(jsonPath("$.data.gender").value("male"))
        .andExpect(jsonPath("$.data.description").value("Vegan enthusiast"))
        .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.jpg"));

    // Verify profile was created in database
    UserProfile profile = profileRepository.findByUserId(testUser.getId()).orElseThrow();
    assertThat(profile.getHeightCm()).isEqualByComparingTo("175.5");
    assertThat(profile.getWeightKg()).isEqualByComparingTo("70.2");
    assertThat(profile.getAge()).isEqualTo(25);
    assertThat(profile.getGender()).isEqualTo(UserProfile.Gender.male);
    assertThat(profile.getDescription()).isEqualTo("Vegan enthusiast");
    assertThat(profile.getAvatarUrl()).isEqualTo("https://example.com/avatar.jpg");
  }

  @Test
  void updateProfile_withPartialData_shouldUpdateOnlyProvidedFields() throws Exception {
    // First create a profile
    UserProfile existingProfile =
        UserProfile.builder()
            .user(testUser)
            .heightCm(new BigDecimal("170.0"))
            .weightKg(new BigDecimal("65.0"))
            .age(30)
            .gender(UserProfile.Gender.female)
            .description("Old description")
            .avatarUrl("https://old.com")
            .build();
    profileRepository.save(existingProfile);

    String requestJson =
        """
        {
          "height_cm": 180.0,
          "description": "Updated description"
        }
        """;

    mockMvc
        .perform(
            put("/api/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.heightCm").value(180.0))
        .andExpect(jsonPath("$.data.description").value("Updated description"))
        .andExpect(jsonPath("$.data.weightKg").value(65.0)) // unchanged
        .andExpect(jsonPath("$.data.age").value(30)) // unchanged
        .andExpect(jsonPath("$.data.gender").value("female")) // unchanged
        .andExpect(jsonPath("$.data.avatarUrl").value("https://old.com")); // unchanged
  }

  @Test
  void updateProfile_withoutAuthentication_shouldReturn401() throws Exception {
    String requestJson =
        """
        {
          "height_cm": 175.5
        }
        """;

    mockMvc
        .perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content(requestJson))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateProfile_withNoFields_shouldReturn400() throws Exception {
    String requestJson = "{}";

    mockMvc
        .perform(
            put("/api/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("At least one profile field must be provided"));
  }

  @Test
  void updateProfile_withInvalidHeight_shouldReturn400() throws Exception {
    String requestJson =
        """
        {
          "height_cm": 400
        }
        """;

    mockMvc
        .perform(
            put("/api/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void updateProfile_withInvalidGender_shouldReturn400() throws Exception {
    String requestJson =
        """
        {
          "gender": "invalid"
        }
        """;

    mockMvc
        .perform(
            put("/api/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void updateProfile_withInvalidAvatarUrl_shouldReturn400() throws Exception {
    String requestJson =
        """
        {
          "avatar_url": "not-a-url"
        }
        """;

    mockMvc
        .perform(
            put("/api/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void updateProfile_withDescriptionTooLong_shouldReturn400() throws Exception {
    String longDescription = "a".repeat(2001);
    String requestJson =
        """
        {
          "description": "%s"
        }
        """
            .formatted(longDescription);

    mockMvc
        .perform(
            put("/api/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }
}
