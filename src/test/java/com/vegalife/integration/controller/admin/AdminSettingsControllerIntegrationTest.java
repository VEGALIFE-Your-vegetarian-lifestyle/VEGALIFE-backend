package com.vegalife.integration.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.admin.PlatformSettingRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
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
class AdminSettingsControllerIntegrationTest {

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

  @Autowired private PlatformSettingRepository platformSettingRepository;

  @Autowired private ModerationLogRepository moderationLogRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    User admin =
        userRepository.save(
            User.builder()
                .username("adminuser")
                .email("admin@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.ADMIN)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
    User regular =
        userRepository.save(
            User.builder()
                .username("regularuser")
                .email("user@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.USER)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(regular);
  }

  @Test
  void getSettings_asAdmin_returns200GroupedByCategory() throws Exception {
    mockMvc
        .perform(get("/api/admin/settings").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[?(@.category=='auth')]").exists());
  }

  @Test
  void getSettings_asMember_returns403() throws Exception {
    mockMvc
        .perform(get("/api/admin/settings").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void getSettings_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/settings")).andExpect(status().isUnauthorized());
  }

  @Test
  void updateSettings_asAdmin_appliesChangeAndRecordsAudit() throws Exception {
    mockMvc
        .perform(
            patch("/api/admin/settings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\": {\"max_upload_size_mb\": \"100\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(platformSettingRepository.findByKey("max_upload_size_mb").orElseThrow().getValue())
        .isEqualTo("100");
    assertThat(moderationLogRepository.findAll())
        .anySatisfy(log -> assertThat(log.getAction()).isEqualTo("UPDATE_SETTING"));
  }

  @Test
  void updateSettings_unknownKey_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/admin/settings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\": {\"bogus_key\": \"x\"}}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateSettings_emptyBody_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/admin/settings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\": {}}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateSettings_asMember_returns403() throws Exception {
    mockMvc
        .perform(
            patch("/api/admin/settings")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\": {\"max_upload_size_mb\": \"100\"}}"))
        .andExpect(status().isForbidden());
  }
}
