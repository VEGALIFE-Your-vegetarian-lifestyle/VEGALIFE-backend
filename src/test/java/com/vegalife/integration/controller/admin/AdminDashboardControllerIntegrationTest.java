package com.vegalife.integration.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class AdminDashboardControllerIntegrationTest {

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
                .role(User.Role.ADMIN)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    User regular =
        userRepository.save(
            User.builder()
                .username("regularuser")
                .email("user@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(regular);
  }

  @Test
  void getDashboard_asAdmin_returns200WithDefaultWeekRange() throws Exception {
    mockMvc
        .perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.range.type").value("week"))
        .andExpect(jsonPath("$.data.users.total").isNumber())
        .andExpect(jsonPath("$.data.moderationQueue.pendingPosts").isNumber());
  }

  @Test
  void getDashboard_withCustomRange_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/dashboard")
                .param("range", "custom")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.range.type").value("custom"));
  }

  @Test
  void getDashboard_customRangeMissingDates_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/dashboard")
                .param("range", "custom")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getDashboard_invalidRange_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/dashboard")
                .param("range", "year")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getDashboard_asMember_returns403() throws Exception {
    mockMvc
        .perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void getDashboard_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
  }
}
