package com.vegalife.integration.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.filter.PostFlagDetail;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the V35 and V36 schema effects on a real Postgres: the pgvector extension installs, the
 * {@code post_embedding} table and its cosine HNSW index exist, the store accepts a 768-dimension
 * vector, and {@code post.flag} admits the ADR-023 blocker vocabulary only while a post is held.
 * This is the gate that would have caught the image swap being incomplete, since Flyway runs here
 * just as it does in the other 40 integration contexts.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class PostEmbeddingSchemaIntegrationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PostRepository postRepository;

  @Autowired private UserRepository userRepository;

  @Test
  void pgvectorExtensionIsInstalled() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void embeddingTableHasTheExpectedColumns() {
    Long columns =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = 'post_embedding'"
                + " AND column_name IN ('post_id','embedding','model','created_at')",
            Long.class);
    assertThat(columns).isEqualTo(4L);
  }

  @Test
  void hnswCosineIndexIsBuilt() {
    // Reading the index definition proves both that the index exists and that it is the cosine
    // variant: an L2 index would silently change every similarity without failing anything else.
    String definition =
        jdbcTemplate.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_post_embedding_hnsw'",
            String.class);

    assertThat(definition).contains("hnsw").contains("vector_cosine_ops");
  }

  @Test
  void storeAcceptsA768DimensionVector() {
    Post post = publishablePost();

    jdbcTemplate.update(
        "INSERT INTO post_embedding (post_id, embedding, model) VALUES (?, ?::vector, ?)",
        post.getId(),
        vectorLiteral(768),
        "gemini-embedding-001");

    Double similarity =
        jdbcTemplate.queryForObject(
            "SELECT 1 - (embedding <=> ?::vector) FROM post_embedding WHERE post_id = ?",
            Double.class,
            vectorLiteral(768),
            post.getId());

    // The same vector against itself is a cosine distance of 0, so a similarity of 1.
    assertThat(similarity).isNotNull().isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
  }

  @Test
  void aVectorOfTheWrongWidthIsRejected() {
    Post post = publishablePost();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO post_embedding (post_id, embedding, model) VALUES (?, ?::vector, ?)",
                    post.getId(),
                    vectorLiteral(1024),
                    "gemini-embedding-001"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void heldPostAdmitsTheBlockerVocabulary() {
    Post post = publishablePost();
    post.setStatus(PostStatus.processing);
    post.setFlag(PostFlag.SIMILAR_CONTENT);
    UUID matched = UUID.randomUUID();
    post.setFlagDetail(PostFlagDetail.similarContent(matched, 0.9312, 0.85));

    postRepository.saveAndFlush(post);

    Post reloaded = postRepository.findById(post.getId()).orElseThrow();
    assertThat(reloaded.getFlag()).isEqualTo(PostFlag.SIMILAR_CONTENT);
    assertThat(reloaded.getFlagDetail().path("kind").asText()).isEqualTo("SIMILAR_CONTENT");
    assertThat(reloaded.getFlagDetail().path("postId").asText()).isEqualTo(matched.toString());
    assertThat(reloaded.getFlagDetail().path("similarity").asDouble()).isEqualTo(0.9312);
    assertThat(reloaded.getFlagDetail().path("threshold").asDouble()).isEqualTo(0.85);
  }

  /** ADR-024: the snapshot describes the blocker it hangs off, so the two cannot disagree. */
  @Test
  void aSnapshotWhoseKindDisagreesWithTheFlagIsRejectedByTheDatabase() {
    Post post = publishablePost();
    post.setStatus(PostStatus.processing);
    post.setFlag(PostFlag.FILTER_FAILED);
    post.setFlagDetail(PostFlagDetail.skipped(PostFlagDetail.CAUSE_DISABLED));

    assertThatThrownBy(() -> postRepository.saveAndFlush(post))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** ADR-024: the column holds one object; an array would make {@code ->>} silently return null. */
  @Test
  void aNonObjectSnapshotIsRejectedByTheDatabase() {
    Post post = publishablePost();
    post.setStatus(PostStatus.processing);
    post.setFlag(PostFlag.FILTER_SKIPPED);
    post.setFlagDetail(JsonNodeFactory.instance.arrayNode().add("DISABLED"));

    assertThatThrownBy(() -> postRepository.saveAndFlush(post))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * ADR-023's invariant lives in the schema, not only in the services: a blocker describes a post
   * that is waiting for a decision, so the database refuses one on a post that already has a
   * terminal status.
   */
  @Test
  void aFlagOnAPostOutsideProcessingIsRejectedByTheDatabase() {
    Post post = publishablePost();
    post.setFlag(PostFlag.FILTER_FAILED);

    assertThatThrownBy(() -> postRepository.saveAndFlush(post))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** A persisted post in the default state: no flag, so the insert does not depend on V36. */
  private Post publishablePost() {
    User user =
        userRepository.saveAndFlush(
            User.builder()
                .displayName("embeddingowner")
                .email("embeddingowner@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.USER)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());

    return postRepository.saveAndFlush(
        Post.builder()
            .user(user)
            .title("Rau củ hấp")
            .content("Món chay đơn giản cho bữa tối.")
            .rawContent(OBJECT_MAPPER.createObjectNode().put("type", "doc"))
            .viewCount(0)
            .status(PostStatus.drafted)
            .build());
  }

  private static String vectorLiteral(int dimensions) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < dimensions; i++) {
      if (i > 0) {
        sb.append(',');
      }
      // A unit vector: 1.0 on the first axis, zeros elsewhere.
      sb.append(i == 0 ? "1" : "0");
    }
    return sb.append(']').toString();
  }
}
