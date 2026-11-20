package com.vegalife.integration.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.post.Post;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.admin.AdminDashboardCacheNames;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
    // Pin the session zone to a non-UTC one. The chart queries bucket with date_trunc, which
    // truncates in the session timezone unless told otherwise; on a UTC-configured CI machine a
    // regression there would otherwise go unnoticed until it hit a non-UTC server.
    registry.add(
        "spring.datasource.hikari.connection-init-sql", () -> "SET TIME ZONE 'Asia/Bangkok'");
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private PostRepository postRepository;

  @Autowired private PaymentLedgerRepository paymentLedgerRepository;

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private CacheManager cacheManager;

  @Autowired private ObjectMapper objectMapper;

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    // The dashboard caches are real beans, so a payload built by one test survives its rollback and
    // would be served to the next one. Clearing per test keeps each assertion reading its own data.
    cacheManager.getCache(AdminDashboardCacheNames.ADMIN_DASHBOARD_SUMMARY_CACHE).clear();
    cacheManager.getCache(AdminDashboardCacheNames.ADMIN_DASHBOARD_CHARTS_CACHE).clear();

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
        .andExpect(jsonPath("$.data.publishedPosts.points.length()").value(7));
  }

  @Test
  void getCharts_noLongerReturnsTheRetiredSeries() throws Exception {
    mockMvc
        .perform(get("/api/admin/dashboard/charts").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.revenueTrend").doesNotExist())
        .andExpect(jsonPath("$.data.paymentHealth").doesNotExist())
        .andExpect(jsonPath("$.data.growth").doesNotExist());
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
        .andExpect(jsonPath("$.data.publishedPosts.points.length()").value(24));
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
        .andExpect(jsonPath("$.data.publishedPosts.points").isArray())
        .andExpect(jsonPath("$.data.publishedPosts.points[0].bucketStart").exists());
  }

  @Test
  void getCharts_countsOnlyPublishedPostsAndBucketsThemByPublishedAt() throws Exception {
    User author = saveMember("postauthor", "postauthor@example.com");

    // Counted: published, not deleted, released inside the window.
    savePost(
        author, "Published today", PostStatus.published, Instant.now().minus(1, ChronoUnit.HOURS));
    savePost(
        author,
        "Published two days ago",
        PostStatus.published,
        Instant.now().minus(2, ChronoUnit.DAYS));

    // Excluded: written earlier but never released.
    savePost(author, "Still a draft", PostStatus.drafted, null);
    savePost(author, "Waiting on a human", PostStatus.pending, null);
    savePost(author, "Rejected", PostStatus.unpublished, null);

    // Excluded: published long before the window started.
    savePost(
        author,
        "Published a year ago",
        PostStatus.published,
        Instant.now().minus(365, ChronoUnit.DAYS));

    // Excluded: published inside the window but since soft-deleted.
    savePost(
        author,
        "Published then deleted",
        PostStatus.published,
        Instant.now().minus(1, ChronoUnit.DAYS),
        Instant.now().minus(12, ChronoUnit.HOURS));

    MvcResult result =
        mockMvc
            .perform(
                get("/api/admin/dashboard/charts").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode points =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("publishedPosts")
            .path("points");

    long published = 0;
    for (JsonNode point : points) {
      published += point.path("published").asLong();
    }
    // Only the two live, in-window published posts count. A draft, a queued post, a rejected post,
    // an out-of-window post and a soft-deleted post must all stay out of the series.
    assertThat(published).isEqualTo(2L);
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

  private User saveMember(String displayName, String email) {
    return userRepository.save(
        User.builder()
            .displayName(displayName)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(UserRole.USER)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build());
  }

  private void savePost(User author, String title, PostStatus status, Instant publishedAt) {
    savePost(author, title, status, publishedAt, null);
  }

  private void savePost(
      User author, String title, PostStatus status, Instant publishedAt, Instant deletedAt) {
    // saveAndFlush: the chart queries go through JdbcTemplate, which reads the database directly
    // and would not see a row JPA is still holding in the persistence context.
    postRepository.saveAndFlush(
        Post.builder()
            .user(author)
            .title(title)
            .content("Content for " + title)
            .rawContent(JsonNodeFactory.instance.objectNode())
            .status(status)
            .viewCount(0)
            .publishedAt(publishedAt)
            .deletedAt(deletedAt)
            .build());
  }

  private void saveSucceededPayment(UUID userId, String txnRef, Instant paidAt) {
    // saveAndFlush: the chart queries go through JdbcTemplate, which reads the database directly
    // and
    // would not see a row JPA is still holding in the persistence context.
    paymentLedgerRepository.saveAndFlush(
        PaymentLedger.builder()
            .userId(userId)
            .amount(49000L)
            .currency("VND")
            .status(PaymentLedgerStatus.succeeded)
            .provider("vnpay")
            .txnRef(txnRef)
            .paidAt(paidAt)
            .build());
  }
}
