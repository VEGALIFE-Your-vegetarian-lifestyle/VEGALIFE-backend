package com.vegalife.integration.controller.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** GET /api/posts/{postId}/related (issue #7): pgvector cosine-similarity ranking. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class RelatedPostsIntegrationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String MODEL = "gemini-embedding-001";

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

  @Autowired private PostRepository postRepository;

  @Autowired private UserRepository userRepository;

  @Test
  void getRelatedPosts_ranksByCosineSimilarityExcludingSourceAndNonPublished() throws Exception {
    Post source = publishedPost("Source");
    Post close = publishedPost("Close match");
    Post far = publishedPost("Far match");
    Post draft = draftPost("Unpublished");

    embed(source.getId(), unitVector(0));
    embed(close.getId(), nearUnitVector(0, 0.01));
    embed(far.getId(), unitVector(1));
    embed(draft.getId(), unitVector(0));

    mockMvc
        .perform(get("/api/posts/{postId}/related", source.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].post.id").value(close.getId().toString()))
        .andExpect(jsonPath("$.data[1].post.id").value(far.getId().toString()));
  }

  @Test
  void getRelatedPosts_respectsLimitParameter() throws Exception {
    Post source = publishedPost("Source");
    Post a = publishedPost("A");
    Post b = publishedPost("B");
    embed(source.getId(), unitVector(0));
    embed(a.getId(), nearUnitVector(0, 0.01));
    embed(b.getId(), nearUnitVector(0, 0.02));

    mockMvc
        .perform(get("/api/posts/{postId}/related", source.getId()).param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void getRelatedPosts_sourceWithNoEmbeddingReturnsEmptyArray() throws Exception {
    Post source = publishedPost("No embedding");

    mockMvc
        .perform(get("/api/posts/{postId}/related", source.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void getRelatedPosts_unknownPostReturns404() throws Exception {
    mockMvc
        .perform(get("/api/posts/{postId}/related", java.util.UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  private void embed(java.util.UUID postId, String vector) {
    jdbcTemplate.update(
        "INSERT INTO post_embedding (post_id, embedding, model) VALUES (?, ?::vector, ?)",
        postId,
        vector,
        MODEL);
  }

  private Post publishedPost(String title) {
    return postRepository.saveAndFlush(basePost(title).status(Post.Status.published).build());
  }

  private Post draftPost(String title) {
    return postRepository.saveAndFlush(basePost(title).status(Post.Status.created).build());
  }

  private Post.PostBuilder basePost(String title) {
    User user =
        userRepository.saveAndFlush(
            User.builder()
                .username("author" + java.util.UUID.randomUUID().toString().substring(0, 8))
                .email("author" + java.util.UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    return Post.builder()
        .user(user)
        .title(title)
        .content("Content for " + title)
        .rawContent(OBJECT_MAPPER.createObjectNode().put("type", "doc"))
        .viewCount(0);
  }

  private static String unitVector(int axis) {
    return nearUnitVector(axis, 0.0);
  }

  /** A 768-dim vector with 1.0 on {@code axis}, perturbed slightly on the next axis. */
  private static String nearUnitVector(int axis, double perturbation) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < 768; i++) {
      if (i > 0) {
        sb.append(',');
      }
      double value = i == axis ? 1.0 : (i == axis + 1 ? perturbation : 0.0);
      sb.append(value);
    }
    return sb.append(']').toString();
  }
}
