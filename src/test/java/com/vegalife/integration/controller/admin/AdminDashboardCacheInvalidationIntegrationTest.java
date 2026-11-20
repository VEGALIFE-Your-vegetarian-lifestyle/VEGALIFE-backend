package com.vegalife.integration.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.admin.AdminDashboardCacheNames;
import com.vegalife.service.token.JwtTokenService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The dashboard stops lying the moment an admin acts, end to end against Postgres.
 *
 * <p>Deliberately <em>not</em> {@code @Transactional}. Every eviction is registered to fire on
 * commit, so inside a test-managed transaction the commit never happens and an eviction that was
 * never written would still leave these assertions green. Here the write commits for real, which is
 * the only way to prove the sequence actually runs.
 *
 * <p>Isolation therefore comes from truncating the tables this class touches rather than from
 * rollback.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminDashboardCacheInvalidationIntegrationTest {

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

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private UserRepository userRepository;

  @Autowired private PostRepository postRepository;

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private CacheManager cacheManager;

  @Autowired private ObjectMapper objectMapper;

  private User admin;
  private User member;
  private String adminToken;
  private Category category;

  @BeforeEach
  void setUp() {
    // CASCADE because every one of these tables is reachable from `post` or `user`; truncating
    // them individually would need the foreign keys ordered by hand. `"user"` must be quoted: it is
    // a reserved word in Postgres.
    jdbcTemplate.execute(
        "TRUNCATE TABLE post, comment, vote, saved_item, media, category, \"user\""
            + " RESTART IDENTITY CASCADE");

    // The caches are real singleton beans and these tests commit, so an entry written by one test
    // would otherwise be served to the next one. Each test must populate the cache itself for its
    // "was cached" precondition to mean anything.
    summaryCache().clear();
    chartsCache().clear();

    admin = createUser("cacheadmin", "cache.admin@example.com", UserRole.ADMIN);
    member = createUser("cachemember", "cache.member@example.com", UserRole.USER);
    adminToken = jwtTokenService.generateAccessToken(admin);
    category = categoryRepository.saveAndFlush(Category.builder().name("Dinner").build());
  }

  @Test
  void approvingAPost_clearsTheSummarySoTheQueueCountDropsImmediately() throws Exception {
    Post post = createPendingPost("Held for review");

    // Populate the cache with a real payload that currently reports one pending post.
    assertThat(readPendingPosts()).isEqualTo(1L);
    assertThat(summaryCache().get(summaryKey())).as("summary was cached").isNotNull();

    approve(post);

    // Without eviction this would still read 1 for up to five minutes, because the entry was
    // written above and the TTL has not elapsed.
    assertThat(readPendingPosts()).isEqualTo(0L);
  }

  @Test
  void approvingAPost_clearsTheChartsSoThePublishedSeriesMovesImmediately() throws Exception {
    Post post = createPendingPost("About to be published");

    long before = readPublishedPostsInCurrentWeek();
    assertThat(chartsCache().get("week")).as("charts were cached").isNotNull();

    approve(post);

    assertThat(readPublishedPostsInCurrentWeek()).isEqualTo(before + 1);
  }

  @Test
  void rejectingAPost_clearsTheSummaryQueueCount() throws Exception {
    Post post = createPendingPost("Held then refused");

    assertThat(readPendingPosts()).isEqualTo(1L);
    assertThat(summaryCache().get(summaryKey())).isNotNull();

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{\"reason\":\"not vegan\"}"))
        .andExpect(status().isOk());

    // A rejected post is `unpublished`, not `pending`: it leaves the queue either way.
    assertThat(readPendingPosts()).isEqualTo(0L);
  }

  @Test
  void suspendingAUser_clearsTheSummaryUserCounts() throws Exception {
    assertThat(readSuspendedUsers()).isEqualTo(0L);
    assertThat(summaryCache().get(summaryKey())).isNotNull();

    mockMvc
        .perform(
            post("/api/admin/users/" + member.getId() + "/suspend")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    assertThat(readSuspendedUsers()).isEqualTo(1L);
  }

  @Test
  void castingAVote_leavesTheSummaryCacheWarm() throws Exception {
    // A published post is needed to vote on, and approving it evicts; so the cache is populated
    // after that write, immediately before the vote.
    Post post = createPendingPost("Votable");
    approve(post);
    long votesBefore = readTotalVotes();
    assertThat(summaryCache().get(summaryKey())).isNotNull();

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/posts/" + post.getId() + "/votes")
                .header("Authorization", "Bearer " + memberToken())
                .contentType("application/json")
                .content("{\"voteType\":\"UPVOTE\"}"))
        .andExpect(status().isOk());

    // Deliberate: votes are the highest-volume write on the platform and totalVotes is not an
    // admin-actionable queue. Evicting here would keep a 26-query summary permanently cold to buy
    // freshness on a number nobody moderates. The TTL is the agreed staleness bound instead.
    assertThat(summaryCache().get(summaryKey())).as("vote must not evict").isNotNull();
    assertThat(readTotalVotes()).isEqualTo(votesBefore);
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private String memberToken() {
    return jwtTokenService.generateAccessToken(member);
  }

  private Cache summaryCache() {
    return cacheManager.getCache(AdminDashboardCacheNames.ADMIN_DASHBOARD_SUMMARY_CACHE);
  }

  private Cache chartsCache() {
    return cacheManager.getCache(AdminDashboardCacheNames.ADMIN_DASHBOARD_CHARTS_CACHE);
  }

  /** {@code getSummary()} takes no arguments, so its cache key is the empty simple key. */
  private Object summaryKey() {
    return org.springframework.cache.interceptor.SimpleKey.EMPTY;
  }

  private long readPendingPosts() throws Exception {
    return readDashboard().path("data").path("moderationQueue").path("pendingPosts").asLong();
  }

  private long readSuspendedUsers() throws Exception {
    return readDashboard().path("data").path("users").path("suspended").asLong();
  }

  private long readTotalVotes() throws Exception {
    return readDashboard().path("data").path("engagement").path("totalVotes").asLong();
  }

  private JsonNode readDashboard() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private long readPublishedPostsInCurrentWeek() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/admin/dashboard/charts")
                    .param("range", "week")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode points =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("publishedPosts")
            .path("points");
    long total = 0;
    for (JsonNode point : points) {
      total += point.path("published").asLong();
    }
    return total;
  }

  private void approve(Post post) throws Exception {
    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/approve")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  private User createUser(String displayName, String email, UserRole role) {
    return userRepository.saveAndFlush(
        User.builder()
            .displayName(displayName)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(role)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build());
  }

  private Post createPendingPost(String title) {
    return postRepository.saveAndFlush(
        Post.builder()
            .user(member)
            .title(title)
            .content("Content for " + title)
            .rawContent(JsonNodeFactory.instance.objectNode())
            .status(PostStatus.pending)
            .viewCount(0)
            .categories(Set.of(category))
            .build());
  }
}
