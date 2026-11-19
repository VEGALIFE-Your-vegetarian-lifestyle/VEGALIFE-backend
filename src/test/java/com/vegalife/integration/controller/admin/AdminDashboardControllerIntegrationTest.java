package com.vegalife.integration.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
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
                .displayName("adminuser")
                .email("admin@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.ADMIN)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
    User regular =
        userRepository.save(
            User.builder()
                .displayName("regularuser")
                .email("user@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.USER)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(regular);
  }

  // ---------------------------------------------------------------------
  // Summary: lifetime snapshot, no time filter
  // ---------------------------------------------------------------------

  @Test
  void getDashboard_asAdmin_returnsLifetimeSnapshot() throws Exception {
    mockMvc
        .perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.users.total").isNumber())
        .andExpect(jsonPath("$.data.content.totalPosts").isNumber())
        .andExpect(jsonPath("$.data.moderationQueue.pendingPosts").value(0))
        .andExpect(jsonPath("$.data.money.revenueTotal").value(0))
        .andExpect(jsonPath("$.data.meta.currency").value("VND"))
        .andExpect(jsonPath("$.data.meta.generatedAt").exists());
  }

  @Test
  void getDashboard_hasNoRangeFieldBecauseItTakesNoTimeFilter() throws Exception {
    mockMvc
        .perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.range").doesNotExist());
  }

  @Test
  void getDashboard_ignoresAnyRangeParameterBecauseItHasNone() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/dashboard")
                .param("range", "year")
                .param("from", "2026-01-01")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
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

  // ---------------------------------------------------------------------
  // Charts: the only time-filtered surface
  // ---------------------------------------------------------------------

  @Test
  void getCharts_defaultsToWeekWithDailyBuckets() throws Exception {
    mockMvc
        .perform(get("/api/admin/dashboard/charts").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.meta.range").value("week"))
        .andExpect(jsonPath("$.data.meta.interval").value("day"))
        .andExpect(jsonPath("$.data.meta.boundary").value("inclusiveStartExclusiveEnd"))
        .andExpect(jsonPath("$.data.meta.currency").value("VND"))
        .andExpect(jsonPath("$.data.revenueTrend.points.length()").value(7))
        .andExpect(jsonPath("$.data.paymentHealth.points.length()").value(7))
        .andExpect(jsonPath("$.data.growth.points.length()").value(7));
  }

  @Test
  void getCharts_dayRangeUsesHourlyBuckets() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/dashboard/charts")
                .param("range", "day")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.meta.interval").value("hour"))
        .andExpect(jsonPath("$.data.revenueTrend.points.length()").value(24));
  }

  @Test
  void getCharts_monthRangeUsesDailyBuckets() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/dashboard/charts")
                .param("range", "month")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.meta.interval").value("day"))
        .andExpect(jsonPath("$.data.revenueTrend.points").isArray())
        .andExpect(jsonPath("$.data.revenueTrend.points[0].bucketStart").exists());
  }

  @Test
  void getCharts_customRangeIsRejected() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/dashboard/charts")
                .param("range", "custom")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getCharts_invalidRange_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/dashboard/charts")
                .param("range", "year")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getCharts_asMember_returns403() throws Exception {
    mockMvc
        .perform(get("/api/admin/dashboard/charts").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void getCharts_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/dashboard/charts")).andExpect(status().isUnauthorized());
  }
}
