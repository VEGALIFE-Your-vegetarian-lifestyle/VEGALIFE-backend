package com.vegalife.integration.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
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
 * vector, and {@code post.flag} admits the duplicate verdict. This is the gate that would have
 * caught the image swap being incomplete, since Flyway runs here just as it does in the other 40
 * integration contexts.
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
  void postFlagAdmitsTheDuplicateVerdict() {
    Post post = publishablePost();
    post.setFlag(Post.Flag.DUPLICATE);

    postRepository.saveAndFlush(post);

    Post reloaded = postRepository.findById(post.getId()).orElseThrow();
    assertThat(reloaded.getFlag()).isEqualTo(Post.Flag.DUPLICATE);
  }

  /** A persisted post in the default state: no flag, so the insert does not depend on V36. */
  private Post publishablePost() {
    User user =
        userRepository.saveAndFlush(
            User.builder()
                .username("embeddingowner")
                .email("embeddingowner@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());

    return postRepository.saveAndFlush(
        Post.builder()
            .user(user)
            .title("Rau củ hấp")
            .content("Món chay đơn giản cho bữa tối.")
            .rawContent(OBJECT_MAPPER.createObjectNode().put("type", "doc"))
            .viewCount(0)
            .status(Post.Status.created)
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
