package com.vegalife.integration.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
class ModeratePostIntegrationTest {

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

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private MediaRepository mediaRepository;

  @Autowired private ModerationLogRepository moderationLogRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User admin;
  private User member;
  private String adminToken;
  private String userToken;
  private Category category;

  @BeforeEach
  void setUp() {
    moderationLogRepository.deleteAll();
    postRepository.deleteAll();
    mediaRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    admin = createUser("adminmod", "admin.mod@example.com", User.Role.ADMIN);
    member = createUser("membermod", "member.mod@example.com", User.Role.USER);
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(member);
    category = categoryRepository.saveAndFlush(Category.builder().name("Dinner").build());
  }

  @Test
  void moderate_publish_flipsStatusAndLogsReason_leavingFlagUntouched() throws Exception {
    Post post =
        createPost(
            member, Post.Status.unpublished, Post.Flag.REJECTED, new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/moderate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PUBLISH\",\"reason\":\"Vegan recipe false positive\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Post published successfully"))
        .andExpect(jsonPath("$.data.status").value("published"))
        .andExpect(jsonPath("$.data.flag").value("REJECTED"))
        .andExpect(jsonPath("$.data.publishedAt").exists());

    Post reloaded = postRepository.findById(post.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(Post.Status.published);
    assertThat(reloaded.getFlag()).isEqualTo(Post.Flag.REJECTED);
    assertThat(reloaded.getPublishedAt()).isNotNull();

    List<ModerationLog> logs = moderationLogRepository.findAll();
    assertThat(logs).hasSize(1);
    ModerationLog entry = logs.get(0);
    assertThat(entry.getActorId()).isEqualTo(admin.getId());
    assertThat(entry.getAction()).isEqualTo("PUBLISH_POST");
    assertThat(entry.getTargetType()).isEqualTo("POST");
    assertThat(entry.getTargetId()).isEqualTo(post.getId());
    assertThat(entry.getReason()).isEqualTo("Vegan recipe false positive");
  }

  @Test
  void moderate_unpublish_flipsStatusAndClearsPublishedAt() throws Exception {
    Post post =
        createPost(
            member, Post.Status.published, Post.Flag.PASSED, new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/moderate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"UNPUBLISH\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Post unpublished successfully"))
        .andExpect(jsonPath("$.data.status").value("unpublished"))
        .andExpect(jsonPath("$.data.flag").value("PASSED"))
        .andExpect(jsonPath("$.data.publishedAt").doesNotExist());

    Post reloaded = postRepository.findById(post.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(Post.Status.unpublished);
    assertThat(reloaded.getPublishedAt()).isNull();

    List<ModerationLog> logs = moderationLogRepository.findAll();
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getAction()).isEqualTo("UNPUBLISH_POST");
    assertThat(logs.get(0).getReason()).isNull();
  }

  @Test
  void moderate_publishWithoutCategory_returns400AndNoLog() throws Exception {
    Post post = createPost(member, Post.Status.unpublished, Post.Flag.REJECTED, new HashSet<>());

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/moderate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PUBLISH\",\"reason\":\"trying\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message").value("At least one category is required to publish a post"));

    assertThat(moderationLogRepository.findAll()).isEmpty();
  }

  @Test
  void moderate_publishAlreadyPublished_isNoOpAndLogsNothing() throws Exception {
    Post post =
        createPost(
            member, Post.Status.published, Post.Flag.PASSED, new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/moderate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PUBLISH\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("published"));

    assertThat(moderationLogRepository.findAll()).isEmpty();
  }

  @Test
  void moderate_hiddenPost_returns400() throws Exception {
    Post post =
        createPost(member, Post.Status.hidden, Post.Flag.REJECTED, new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/moderate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PUBLISH\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void moderate_missingPost_returns404() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/posts/00000000-0000-0000-0000-000000000000/moderate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"UNPUBLISH\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void moderate_missingAction_returns400() throws Exception {
    Post post =
        createPost(
            member, Post.Status.published, Post.Flag.PASSED, new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/moderate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"no action\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void moderate_withNonAdminJwt_returns403() throws Exception {
    Post post =
        createPost(
            member, Post.Status.unpublished, Post.Flag.REJECTED, new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/moderate")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PUBLISH\"}"))
        .andExpect(status().isForbidden());

    assertThat(moderationLogRepository.findAll()).isEmpty();
  }

  @Test
  void moderate_withoutJwt_returns401() throws Exception {
    Post post =
        createPost(
            member, Post.Status.unpublished, Post.Flag.REJECTED, new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/moderate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PUBLISH\"}"))
        .andExpect(status().isUnauthorized());
  }

  private User createUser(String username, String email, User.Role role) {
    return userRepository.save(
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(role)
            .status(User.Status.activated)
            .emailVerified(true)
            .build());
  }

  private Post createPost(
      User owner, Post.Status status, Post.Flag flag, Set<Category> categories) {
    return postRepository.saveAndFlush(
        Post.builder()
            .user(owner)
            .title("Moderated post")
            .content("Post content")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .categories(categories)
            .status(status)
            .flag(flag)
            .publishedAt(status == Post.Status.published ? Instant.now() : null)
            .viewCount(0)
            .build());
  }
}
