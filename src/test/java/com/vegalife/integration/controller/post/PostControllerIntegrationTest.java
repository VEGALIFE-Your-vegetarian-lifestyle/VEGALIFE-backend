package com.vegalife.integration.controller.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.util.UUID;
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

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private ModerationLogRepository moderationLogRepository;

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
                      "type": "blog",
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
  void createPost_publishWithActiveCategory_publishesPost() throws Exception {
    Category category = categoryRepository.saveAndFlush(Category.builder().name("Recipes").build());

    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Vegan tofu bowl",
                      "type": "blog",
                      "content": "A simple plant-based lunch.",
                      "categoryIds": ["%s"],
                      "publish": true
                    }
                    """
                        .formatted(category.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("published"))
        .andExpect(jsonPath("$.data.type").value("blog"))
        .andExpect(jsonPath("$.data.categoryIds[0]").value(category.getId().toString()));

    assertThat(postRepository.findAll().getFirst().getPublishedAt()).isNotNull();
  }

  @Test
  void createPost_publishWithoutCategory_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"type\":\"blog\",\"content\":\"C\",\"publish\":true}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createPost_videoWithoutFileOrLink_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"type\":\"video\"}"))
        .andExpect(status().isBadRequest());
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
                .content("{\"title\":\" \",\"type\":\"blog\",\"content\":\"Test content\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void updatePost_withOneField_updatesOnlyThatField() throws Exception {
    Post existingPost = createPost(user, "Old title", Post.Status.published, null);
    existingPost.setCategories(
        new java.util.HashSet<>(
            java.util.Set.of(
                categoryRepository.saveAndFlush(Category.builder().name("Recipes").build()))));
    existingPost.setContent("Keep this content");
    existingPost.setFeaturedImageUrl("https://example.com/old.jpg");
    existingPost.setViewCount(12);
    existingPost = postRepository.saveAndFlush(existingPost);

    mockMvc
        .perform(
            patch("/api/posts/{postId}", existingPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New title\",\"content\":null,\"featuredImageUrl\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Post updated successfully"))
        .andExpect(jsonPath("$.data.title").value("New title"))
        .andExpect(jsonPath("$.data.content").value("Keep this content"))
        .andExpect(jsonPath("$.data.featuredImageUrl").value("https://example.com/old.jpg"))
        .andExpect(jsonPath("$.data.status").value("published"))
        .andExpect(jsonPath("$.data.viewCount").value(12));

    Post updatedPost = postRepository.findById(existingPost.getId()).orElseThrow();
    assertThat(updatedPost.getUser().getId()).isEqualTo(user.getId());
    assertThat(updatedPost.getStatus()).isEqualTo(Post.Status.published);
    assertThat(updatedPost.getViewCount()).isEqualTo(12);
  }

  @Test
  void updatePost_canChangeContentAndFeaturedImage() throws Exception {
    Post existingPost = createPost(user, "Post title", Post.Status.created, null);

    mockMvc
        .perform(
            patch("/api/posts/{postId}", existingPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"content\":\"Updated content\",\"featuredImageUrl\":\"https://example.com/new.jpg\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("Post title"))
        .andExpect(jsonPath("$.data.content").value("Updated content"))
        .andExpect(jsonPath("$.data.featuredImageUrl").value("https://example.com/new.jpg"));
  }

  @Test
  void updatePost_withoutJwt_returns401() throws Exception {
    mockMvc
        .perform(
            patch("/api/posts/{postId}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New title\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updatePost_withInvalidFields_returns400() throws Exception {
    Post existingPost = createPost(user, "Old title", Post.Status.created, null);
    String path = "/api/posts/" + existingPost.getId();

    mockMvc
        .perform(
            patch(path)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));

    mockMvc
        .perform(
            patch(path)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));

    mockMvc
        .perform(
            patch(path)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\" \"}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            patch(path)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + "x".repeat(256) + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updatePost_whenPostIsMissingDeletedOrOwnedByAnotherUser_returns404() throws Exception {
    Post deletedPost = createPost(user, "Deleted post", Post.Status.created, Instant.now());
    Post otherUsersPost = createPost(otherUser, "Other user's post", Post.Status.created, null);
    String body = "{\"title\":\"New title\"}";

    mockMvc
        .perform(
            patch("/api/posts/{postId}", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Post not found"));

    mockMvc
        .perform(
            patch("/api/posts/{postId}", deletedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            patch("/api/posts/{postId}", otherUsersPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());

    assertThat(postRepository.findById(otherUsersPost.getId()).orElseThrow().getTitle())
        .isEqualTo("Other user's post");
  }

  @Test
  void updatePost_changingTypeReturns400() throws Exception {
    Post existingPost = createPost(user, "Post title", Post.Status.created, null);

    mockMvc
        .perform(
            patch("/api/posts/{postId}", existingPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"video\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Post type cannot be changed"));
  }

  @Test
  void updatePost_publishesDraftWhenCategoryAssigned() throws Exception {
    Post existingPost = createPost(user, "Post title", Post.Status.created, null);
    Category category = categoryRepository.saveAndFlush(Category.builder().name("Recipes").build());

    mockMvc
        .perform(
            patch("/api/posts/{postId}", existingPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":[\"%s\"],\"publish\":true}".formatted(category.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("published"))
        .andExpect(jsonPath("$.data.categoryIds[0]").value(category.getId().toString()));
  }

  @Test
  void updatePost_publishWithoutCategoryReturns400() throws Exception {
    Post existingPost = createPost(user, "Post title", Post.Status.created, null);

    mockMvc
        .perform(
            patch("/api/posts/{postId}", existingPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"publish\":true}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updatePost_adminCanEditAnotherUsersPostAndActionIsLogged() throws Exception {
    User admin = createUser("adminuser", "admin@example.com", User.Role.ADMIN);
    Post otherUsersPost = createPost(otherUser, "Other title", Post.Status.created, null);

    mockMvc
        .perform(
            patch("/api/posts/{postId}", otherUsersPost.getId())
                .header("Authorization", "Bearer " + jwtTokenService.generateAccessToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Moderated title\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("Moderated title"));

    assertThat(moderationLogRepository.findAll())
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getActorId()).isEqualTo(admin.getId());
              assertThat(log.getTargetId()).isEqualTo(otherUsersPost.getId());
              assertThat(log.getAction()).isEqualTo("EDIT_POST");
            });
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
    return createUser(username, email, User.Role.USER);
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

  private Post createPost(User owner, String title, Post.Status status, Instant deletedAt) {
    return postRepository.save(
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
