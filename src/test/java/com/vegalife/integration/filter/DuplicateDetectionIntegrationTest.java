package com.vegalife.integration.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.filter.ContentFilterOutboundAdapter;
import com.vegalife.filter.ContentFilterPayload;
import com.vegalife.filter.ContentFilterService;
import com.vegalife.filter.FilterVerdict;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostEmbeddingRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end duplicate detection against a real Postgres with pgvector: the vector written at
 * filter time is found again by the nearest-neighbour query, and a post similar enough to an
 * existing one is flagged rather than published.
 *
 * <p>The topic verdict and the embedding are both stubbed with fixed unit vectors. That makes the
 * similarity exact and independent of a live embedding provider, so the assertions test the
 * detection path rather than the relevance scorer, which has its own regression suite.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DuplicateDetectionIntegrationTest {

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

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String MODEL = "gemini-embedding-001";

  @Autowired private ContentFilterOutboundAdapter adapter;

  @Autowired private PostRepository postRepository;

  @Autowired private PostEmbeddingRepository postEmbeddingRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  /** Stubbed so the topic verdict and the vector are deterministic; detection is what is tested. */
  @MockBean private ContentFilterService contentFilterService;

  private User author;

  @BeforeEach
  void setUp() {
    // No @Transactional on this class, deliberately. The adapter is @Transactional, so under a
    // test-level transaction the second delivery would run inside the first one's uncommitted
    // transaction - the copy would never be seen, its writes would never be visible, and the
    // duplicate check would be untestable. Each delivery therefore commits, and this hook clears
    // the tables between tests instead.
    jdbcTemplate.update("DELETE FROM post_embedding");
    jdbcTemplate.update("DELETE FROM post_category");
    jdbcTemplate.update("DELETE FROM post_media");
    jdbcTemplate.update("DELETE FROM post");
    jdbcTemplate.update("DELETE FROM \"user\"");

    author =
        userRepository.saveAndFlush(
            User.builder()
                .username("dupowner")
                .email("dupowner@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.USER)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
  }

  @Test
  void aPostNearlyIdenticalToAPublishedOneIsFlaggedNotPublished() throws Exception {
    Post original = published("Phở chay", "Công thức phở chay truyền thống Hà Nội.");
    deliver(original, axis(0));

    Post copy = queued("Phở chay", "Công thức phở chay truyền thống Hà Nội!");
    // A lightly edited copy of the original's vector: same direction, a small tilt. Its cosine
    // similarity to the original is well above the 0.8 threshold.
    deliver(copy, nearly(axis(0), 0.1));

    assertThat(state(copy)).isEqualTo("unpublished/DUPLICATE");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT published_at FROM post WHERE id = ?",
                java.sql.Timestamp.class,
                copy.getId()))
        .isNull();
  }

  @Test
  void anUnrelatedPostStillPublishes() throws Exception {
    Post original = published("Phở chay", "Công thức phở chay truyền thống Hà Nội.");
    deliver(original, axis(0));

    Post unrelated = queued("Bún riêu", "Món bún riêu chay miền Nam.");
    // Orthogonal to the original: similarity exactly 0.0, so "unrelated" needs no tuned constant.
    deliver(unrelated, axis(1));

    assertThat(state(unrelated)).isEqualTo("published/PASSED");
  }

  @Test
  void thePublishedPostStoresItsVector() throws Exception {
    Post original = published("Phở chay", "Công thức phở chay truyền thống Hà Nội.");
    deliver(original, axis(0));

    assertThat(storedRows(original)).isEqualTo(1L);
  }

  @Test
  void aFlaggedDuplicateDoesNotBecomeAComparisonTarget() throws Exception {
    Post original = published("Phở chay", "Công thức phở chay truyền thống Hà Nội.");
    deliver(original, axis(0));

    Post copy = queued("Phở chay", "Công thức phở chay truyền thống Hà Nội!");
    deliver(copy, nearly(axis(0), 0.1));

    // Unpublished posts are never targets; storing one would let it start flagging other content.
    assertThat(storedRows(copy)).isZero();
  }

  @Test
  void aVectorFromADifferentModelIsNotCompared() throws Exception {
    Post original = published("Phở chay", "Công thức phở chay truyền thống Hà Nội.");
    deliver(original, axis(0));

    // Simulate a row written before a provider change: identical space here, different model name.
    jdbcTemplate.update(
        "UPDATE post_embedding SET model = 'some-older-model' WHERE post_id = ?", original.getId());

    Post copy = queued("Phở chay", "Công thức phở chay truyền thống Hà Nội!");
    deliver(copy, nearly(axis(0), 0.1));

    // Identical direction, but incomparable models, so the copy publishes rather than flagging.
    assertThat(state(copy)).isEqualTo("published/PASSED");
  }

  @Test
  void aDraftIsNeverAComparisonTarget() throws Exception {
    // A stored vector on an unpublished post must be ignored, or one member's draft would flag
    // another member's post.
    Post draft = queued("Phở chay", "Công thức phở chay truyền thống Hà Nội.");
    postEmbeddingRepository.upsert(draft.getId(), axis(0), MODEL);

    Post incoming = queued("Phở chay", "Công thức phở chay truyền thống Hà Nội!");
    deliver(incoming, nearly(axis(0), 0.1));

    assertThat(state(incoming)).isEqualTo("published/PASSED");
  }

  @Test
  void rePublishingTheSamePostUpsertsRatherThanDuplicating() throws Exception {
    Post post = published("Phở chay", "Công thức phở chay truyền thống Hà Nội.");
    deliver(post, axis(0));
    deliver(post, axis(0));

    // A second row would fail on the primary key rather than pass; this pins the upsert path.
    assertThat(storedRows(post)).isEqualTo(1L);
  }

  /** Runs the adapter with a stubbed PASSED verdict and the given vector. */
  private void deliver(Post post, float[] embedding) throws Exception {
    when(contentFilterService.filter(any()))
        .thenReturn(
            new ContentFilterService.FilterResult(
                new FilterVerdict(PostFlag.PASSED, 0.9, List.of()), embedding));
    OutboundMessage message =
        OutboundMessage.builder()
            .id(UUID.randomUUID())
            .payload(OBJECT_MAPPER.writeValueAsString(new ContentFilterPayload(post.getId())))
            .build();
    adapter.deliver(message);
  }

  private Long storedRows(Post post) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM post_embedding WHERE post_id = ?", Long.class, post.getId());
  }

  private Post published(String title, String content) {
    Post post = queued(title, content);
    post.setStatus(PostStatus.published);
    return postRepository.saveAndFlush(post);
  }

  private Post queued(String title, String content) {
    return postRepository.saveAndFlush(
        Post.builder()
            .user(author)
            .title(title)
            .content(content)
            .rawContent(OBJECT_MAPPER.createObjectNode().put("type", "doc"))
            .viewCount(0)
            .status(PostStatus.created)
            .flag(PostFlag.PENDING)
            .build());
  }

  /** Reads the post back fresh rather than through a persisted-context cache. */
  private String state(Post post) {
    return jdbcTemplate.queryForObject(
        "SELECT status || '/' || coalesce(flag,'no-flag') FROM post WHERE id = ?",
        String.class,
        post.getId());
  }

  /** A unit vector along axis {@code index}. */
  private static float[] axis(int index) {
    float[] v = new float[768];
    v[index] = 1f;
    return v;
  }

  /**
   * A unit vector a small angle away from {@code base} — a lightly edited copy of it.
   *
   * <p>Distinct from a second {@link #axis}: two different axes are <em>orthogonal</em>, similarity
   * {@code 0.0}, which is the opposite of a near-duplicate. Using one where the other was meant
   * makes a duplicate test quietly assert the wrong thing.
   *
   * @param base the vector to tilt away from
   * @param tilt how much weight to move onto the next axis; {@code 0.1} gives similarity ~0.995
   */
  private static float[] nearly(float[] base, double tilt) {
    float[] v = base.clone();
    int other = 0;
    while (other < v.length && v[other] != 0f) {
      other++;
    }
    v[other] = (float) tilt;
    double norm = Math.sqrt(1.0 + tilt * tilt);
    for (int i = 0; i < v.length; i++) {
      v[i] /= (float) norm;
    }
    return v;
  }
}
