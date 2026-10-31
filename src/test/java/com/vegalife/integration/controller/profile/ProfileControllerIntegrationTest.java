package com.vegalife.integration.controller.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vegalife.enums.user.Gender;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.user.DietaryPreferences;
import com.vegalife.model.user.User;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class ProfileControllerIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg15")
          .withDatabaseName("vegalife_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false);

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }

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
            .role(UserRole.USER)
            .status(UserStatus.activated)
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
          "heightCm": 175.5,
          "weightKg": 70.2,
          "age": 25,
          "gender": "male",
          "description": "Vegan enthusiast",
          "avatarUrl": "https://example.com/avatar.jpg"
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
    assertThat(profile.getGender()).isEqualTo(Gender.male);
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
            .gender(Gender.female)
            .description("Old description")
            .avatarUrl("https://old.com")
            .build();
    profileRepository.save(existingProfile);

    String requestJson =
        """
        {
          "heightCm": 180.0,
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
  void updateProfile_withDietaryFields_shouldPersistAsJsonbAndReturnFlatFieldsWithBmi()
      throws Exception {
    String requestJson =
        """
        {
          "heightCm": 170.00,
          "weightKg": 70.00,
          "preferences": ["spicy"],
          "dislikes": ["mushrooms"],
          "allergies": ["peanuts", "shellfish"],
          "restrictions": ["gluten free"],
          "healthGoal": "weight loss"
        }
        """;

    mockMvc
        .perform(
            put("/api/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.preferences[0]").value("spicy"))
        .andExpect(jsonPath("$.data.dislikes[0]").value("mushrooms"))
        .andExpect(jsonPath("$.data.allergies[0]").value("peanuts"))
        .andExpect(jsonPath("$.data.allergies[1]").value("shellfish"))
        .andExpect(jsonPath("$.data.restrictions[0]").value("gluten free"))
        .andExpect(jsonPath("$.data.healthGoal").value("weight loss"))
        // 70 / 1.70^2 = 24.2 — derived at read time, never stored
        .andExpect(jsonPath("$.data.bmi").value(24.2));

    // Persisted as the single JSONB value type, not as flat columns
    UserProfile profile = profileRepository.findByUserId(testUser.getId()).orElseThrow();
    assertThat(profile.getDietaryPreferences().getPreferences()).containsExactly("spicy");
    assertThat(profile.getDietaryPreferences().getAllergies())
        .containsExactly("peanuts", "shellfish");
    assertThat(profile.getDietaryPreferences().getHealthGoal()).isEqualTo("weight loss");
  }

  @Test
  void updateProfile_dietaryPartialUpdate_shouldKeepExistingMembersAndOmitUnsetOnes()
      throws Exception {
    profileRepository.save(
        UserProfile.builder()
            .user(testUser)
            .dietaryPreferences(
                DietaryPreferences.builder()
                    .preferences(List.of("spicy"))
                    .allergies(List.of("peanuts"))
                    .build())
            .build());

    // Only restrictions + healthGoal provided; allergies cleared with an empty list
    String requestJson =
        """
        {
          "restrictions": ["gluten free"],
          "healthGoal": "muscle gain",
          "allergies": []
        }
        """;

    mockMvc
        .perform(
            put("/api/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        // kept because omitted
        .andExpect(jsonPath("$.data.preferences[0]").value("spicy"))
        // cleared because an empty list was sent
        .andExpect(jsonPath("$.data.allergies").isEmpty())
        .andExpect(jsonPath("$.data.restrictions[0]").value("gluten free"))
        .andExpect(jsonPath("$.data.healthGoal").value("muscle gain"))
        .andExpect(jsonPath("$.data.bmi").doesNotExist());
  }

  @Test
  void getOwnProfile_withoutDietaryData_shouldOmitTheFieldsAndBmi() throws Exception {
    mockMvc
        .perform(get("/api/profile").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.preferences").doesNotExist())
        .andExpect(jsonPath("$.data.healthGoal").doesNotExist())
        .andExpect(jsonPath("$.data.bmi").doesNotExist());
  }

  @Test
  void updateProfile_withDietaryTagTooLong_shouldReturn400() throws Exception {
    String tooLong = "a".repeat(101);
    String requestJson =
        """
        {
          "allergies": ["%s"]
        }
        """
            .formatted(tooLong);

    mockMvc
        .perform(
            put("/api/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void updateProfile_withoutAuthentication_shouldReturn401() throws Exception {
    String requestJson =
        """
        {
          "heightCm": 175.5
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
          "heightCm": 400
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
          "avatarUrl": "not-a-url"
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
  void getOwnProfile_withAuthentication_shouldReturn200AndProfile() throws Exception {
    mockMvc
        .perform(get("/api/profile").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Profile retrieved successfully"))
        .andExpect(jsonPath("$.data.userId").value(testUser.getId().toString()))
        .andExpect(jsonPath("$.data.username").value("profileuser"))
        .andExpect(jsonPath("$.data.email").value("profile@example.com"));

    // Reading a user without a profile row must have created an empty one
    assertThat(profileRepository.findByUserId(testUser.getId())).isPresent();
  }

  @Test
  void getOwnProfile_withoutAuthentication_shouldReturn401() throws Exception {
    mockMvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
  }

  @Test
  void getProfileByUserId_withoutAuthentication_shouldReturn200AndProfile() throws Exception {
    mockMvc
        .perform(get("/api/profile/{userId}", testUser.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(testUser.getId().toString()))
        .andExpect(jsonPath("$.data.username").value("profileuser"))
        .andExpect(jsonPath("$.data.email").value("profile@example.com"));
  }

  @Test
  void getProfileByUserId_whenProfileMissing_shouldCreateEmptyProfileAndReturn200()
      throws Exception {
    assertThat(profileRepository.findByUserId(testUser.getId())).isEmpty();

    mockMvc
        .perform(get("/api/profile/{userId}", testUser.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(testUser.getId().toString()))
        .andExpect(jsonPath("$.data.heightCm").doesNotExist())
        .andExpect(jsonPath("$.data.description").doesNotExist());

    assertThat(profileRepository.findByUserId(testUser.getId())).isPresent();
  }

  @Test
  void getProfileByUserId_whenUserDoesNotExist_shouldReturn404() throws Exception {
    mockMvc
        .perform(get("/api/profile/{userId}", "11111111-2222-3333-4444-555555555555"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("User not found"));
  }

  @Test
  void updateProfile_withAvatarUrlOnly_shouldReturn200WithIdentityFieldsAndNoId() throws Exception {
    mockMvc
        .perform(
            put("/api/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"avatarUrl\": \"https://example.com/new-avatar.jpg\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(testUser.getId().toString()))
        .andExpect(jsonPath("$.data.username").value("profileuser"))
        .andExpect(jsonPath("$.data.email").value("profile@example.com"))
        .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/new-avatar.jpg"))
        .andExpect(jsonPath("$.data.id").doesNotExist());
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
