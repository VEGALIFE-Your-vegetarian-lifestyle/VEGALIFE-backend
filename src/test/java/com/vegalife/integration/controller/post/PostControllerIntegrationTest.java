package com.vegalife.integration.controller.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
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
class PostControllerIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
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

  @Autowired private PostRepository postRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User user;
  private User otherUser;
  private String accessToken;

  @BeforeEach
  void setUp() {
    user = createUser("postowner", "postowner@example.com");
    otherUser = createUser("otherowner", "otherowner@example.com");
    accessToken = jwtTokenService.generateAccessToken(user);
  }

  @Test
  void createPost_createsPostForAuthenticatedUser() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Vegan tofu bowl",
                      "content": "A simple plant-based lunch.",
                      "featuredImageUrl": "https://example.com/tofu-bowl.jpg"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Post created successfully"))
        .andExpect(jsonPath("$.data.title").value("Vegan tofu bowl"))
        .andExpect(jsonPath("$.data.status").value("created"))
        .andExpect(jsonPath("$.data.viewCount").value(0));

    Post createdPost = postRepository.findAll().getFirst();
    assertThat(createdPost.getUser().getId()).isEqualTo(user.getId());
    assertThat(createdPost.getStatus()).isEqualTo(Post.Status.created);
    assertThat(createdPost.getPublishedAt()).isNull();
  }

  @Test
  void createPost_withoutJwt_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test\",\"content\":\"Test content\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createPost_withBlankTitle_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"content\":\"Test content\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void listUserPosts_returnsOnlyOwnNonDeletedPostsWithAnyStatus() throws Exception {
    createPost(user, "Published post", Post.Status.published, null);
    createPost(user, "Hidden post", Post.Status.hidden, null);
    createPost(user, "Deleted post", Post.Status.published, Instant.now());
    createPost(otherUser, "Another user's post", Post.Status.published, null);

    mockMvc
        .perform(get("/api/posts").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Posts retrieved successfully"))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(
            jsonPath("$.data.content[*].title")
                .value(containsInAnyOrder("Published post", "Hidden post")))
        .andExpect(
            jsonPath("$.data.content[*].status").value(containsInAnyOrder("published", "hidden")));
  }

  @Test
  void listUserPosts_appliesPagination() throws Exception {
    createPost(user, "First post", Post.Status.created, null);
    createPost(user, "Second post", Post.Status.published, null);
    createPost(user, "Third post", Post.Status.unpublished, null);

    mockMvc
        .perform(
            get("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "0")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(3))
        .andExpect(jsonPath("$.data.content.length()").value(1));
  }

  @Test
  void listUserPosts_withoutJwt_returns401() throws Exception {
    mockMvc.perform(get("/api/posts")).andExpect(status().isUnauthorized());
  }

  @Test
  void listUserPosts_withInvalidPageSize_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/posts").header("Authorization", "Bearer " + accessToken).param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  private User createUser(String username, String email) {
    return userRepository.save(
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build());
  }

  private void createPost(User owner, String title, Post.Status status, Instant deletedAt) {
    postRepository.save(
        Post.builder()
            .user(owner)
            .title(title)
            .content("Post content")
            .status(status)
            .viewCount(0)
            .deletedAt(deletedAt)
            .build());
  }
}
