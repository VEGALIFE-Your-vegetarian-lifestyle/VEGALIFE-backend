package com.vegalife.integration.controller.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.outbound.OutboundMessageRepository;
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

  @Autowired private OutboundMessageRepository outboundMessageRepository;

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
  void getPost_returnsAnotherUsersPublishedPost() throws Exception {
    Post published = createPost(otherUser, "Someone else's post", Post.Status.published, null);

    mockMvc
        .perform(
            get("/api/posts/{postId}", published.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Post retrieved successfully"))
        .andExpect(jsonPath("$.data.id").value(published.getId().toString()))
        .andExpect(jsonPath("$.data.title").value("Someone else's post"));
  }

  @Test
  void getPost_ownNonPublishedPostReturns404() throws Exception {
    Post draft = createPost(user, "My draft", Post.Status.created, null);

    mockMvc
        .perform(
            get("/api/posts/{postId}", draft.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Post not found"));
  }

  @Test
  void getPost_softDeletedPostReturns404() throws Exception {
    Post deleted = createPost(user, "Gone", Post.Status.published, Instant.now());

    mockMvc
        .perform(
            get("/api/posts/{postId}", deleted.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void getPost_unknownIdReturns404() throws Exception {
    mockMvc
        .perform(
            get("/api/posts/{postId}", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void getPost_withoutJwt_returnsPublishedPost() throws Exception {
    Post published = createPost(otherUser, "Public post", Post.Status.published, null);

    mockMvc
        .perform(get("/api/posts/{postId}", published.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(published.getId().toString()));
  }

  @Test
  void getPost_withoutJwt_nonPublishedPostReturns404() throws Exception {
    Post draft = createPost(user, "My draft", Post.Status.created, null);

    mockMvc
        .perform(get("/api/posts/{postId}", draft.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Post not found"));
  }

  @Test
  void getPost_withoutJwt_softDeletedPostReturns404() throws Exception {
    Post deleted = createPost(user, "Gone", Post.Status.published, Instant.now());

    mockMvc.perform(get("/api/posts/{postId}", deleted.getId())).andExpect(status().isNotFound());
  }

  @Test
  void getPost_withoutJwt_unknownIdReturns404() throws Exception {
    mockMvc.perform(get("/api/posts/{postId}", UUID.randomUUID())).andExpect(status().isNotFound());
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
                      "rawContent": {"type": "doc"},
                      "featuredImageUrl": "https://example.com/tofu-bowl.jpg"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Post created successfully"))
        .andExpect(jsonPath("$.data.title").value("Vegan tofu bowl"))
        .andExpect(jsonPath("$.data.rawContent.type").value("doc"))
        .andExpect(jsonPath("$.data.status").value("created"))
        .andExpect(jsonPath("$.data.viewCount").value(0));

    Post createdPost = postRepository.findAll().getFirst();
    assertThat(createdPost.getUser().getId()).isEqualTo(user.getId());
    assertThat(createdPost.getStatus()).isEqualTo(Post.Status.created);
    assertThat(createdPost.getPublishedAt()).isNull();
  }

  @Test
  void createPost_publishWithActiveCategoryQueuesPostForFiltering() throws Exception {
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
                      "content": "A simple plant-based lunch.",
                      "rawContent": {},
                      "categoryIds": ["%s"],
                      "publish": true
                    }
                    """
                        .formatted(category.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("created"))
        .andExpect(jsonPath("$.data.flag").value("PENDING"))
        .andExpect(jsonPath("$.data.categoryIds[0]").value(category.getId().toString()));

    Post queuedPost = postRepository.findAll().getFirst();
    assertThat(queuedPost.getStatus()).isEqualTo(Post.Status.created);
    assertThat(queuedPost.getFlag()).isEqualTo(Post.Flag.PENDING);
    assertThat(queuedPost.getPublishedAt()).isNull();
    assertThat(outboundMessageRepository.findAll())
        .anySatisfy(
            message -> {
              assertThat(message.getChannel()).isEqualTo(OutboundChannel.CONTENT_FILTER);
              assertThat(message.getRecipient()).isEqualTo(queuedPost.getId().toString());
              assertThat(message.getStatus()).isEqualTo(OutboundStatus.PENDING);
            });
  }

  @Test
  void createPost_publishWithoutCategory_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"content\":\"C\",\"rawContent\":{},\"publish\":true}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createPost_withoutRawContent_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"content\":\"C\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void createPost_withRawContentNotAnObject_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"content\":\"C\",\"rawContent\":\"not-an-object\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void createPost_withBlankContent_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"content\":\" \",\"rawContent\":{}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
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
                .content("{\"title\":\" \",\"content\":\"Test content\",\"rawContent\":{}}"))
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
                .content(
                    "{\"title\":\"New title\",\"content\":\"Keep this content\",\"rawContent\":{},"
                        + "\"featuredImageUrl\":null}"))
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
    assertThat(updatedPost.getFlag()).isEqualTo(Post.Flag.PENDING);
    assertThat(updatedPost.getViewCount()).isEqualTo(12);
    assertThat(outboundMessageRepository.findAll())
        .anySatisfy(
            message -> {
              assertThat(message.getChannel()).isEqualTo(OutboundChannel.CONTENT_FILTER);
              assertThat(message.getRecipient()).isEqualTo(updatedPost.getId().toString());
            });
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
                    "{\"content\":\"Updated content\",\"rawContent\":{},"
                        + "\"featuredImageUrl\":\"https://example.com/new.jpg\"}"))
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
                .content("{\"title\":\" \",\"content\":\"Valid content\",\"rawContent\":{}}"))
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
                .content("{\"content\":\" \",\"rawContent\":{}}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            patch(path)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\""
                        + "x".repeat(256)
                        + "\",\"content\":\"Valid content\",\"rawContent\":{}}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            patch(path)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Valid content\",\"rawContent\":\"not-an-object\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updatePost_whenPostIsMissingDeletedOrOwnedByAnotherUser_returns404() throws Exception {
    Post deletedPost = createPost(user, "Deleted post", Post.Status.created, Instant.now());
    Post otherUsersPost = createPost(otherUser, "Other user's post", Post.Status.created, null);
    String body = "{\"title\":\"New title\",\"content\":\"New content\",\"rawContent\":{}}";

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
  void updatePost_publishQueuesDraftForFilteringWhenCategoryAssigned() throws Exception {
    Post existingPost = createPost(user, "Post title", Post.Status.created, null);
    Category category = categoryRepository.saveAndFlush(Category.builder().name("Recipes").build());

    mockMvc
        .perform(
            patch("/api/posts/{postId}", existingPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"content\":\"Post content\",\"rawContent\":{},\"categoryIds\":[\"%s\"],"
                            .formatted(category.getId())
                        + "\"publish\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("created"))
        .andExpect(jsonPath("$.data.flag").value("PENDING"))
        .andExpect(jsonPath("$.data.categoryIds[0]").value(category.getId().toString()));

    Post queuedPost = postRepository.findById(existingPost.getId()).orElseThrow();
    assertThat(queuedPost.getStatus()).isEqualTo(Post.Status.created);
    assertThat(queuedPost.getFlag()).isEqualTo(Post.Flag.PENDING);
    assertThat(queuedPost.getPublishedAt()).isNull();
  }

  @Test
  void updatePost_publishWithoutCategoryReturns400() throws Exception {
    Post existingPost = createPost(user, "Post title", Post.Status.created, null);

    mockMvc
        .perform(
            patch("/api/posts/{postId}", existingPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Post content\",\"rawContent\":{},\"publish\":true}"))
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
                .content(
                    "{\"title\":\"Moderated title\",\"content\":\"Post content\","
                        + "\"rawContent\":{}}"))
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
  void deletePost_ownerSoftDeletesPost() throws Exception {
    Post existingPost = createPost(user, "Post title", Post.Status.created, null);

    mockMvc
        .perform(
            delete("/api/posts/{postId}", existingPost.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Post deleted successfully"));

    assertThat(postRepository.findById(existingPost.getId()).orElseThrow().getDeletedAt())
        .isNotNull();
    mockMvc
        .perform(get("/api/posts").header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.data.totalElements").value(0));
    mockMvc
        .perform(
            delete("/api/posts/{postId}", existingPost.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void deletePost_withoutJwt_returns401() throws Exception {
    mockMvc
        .perform(delete("/api/posts/{postId}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deletePost_ofAnotherUserOrMissing_returns404AndKeepsPost() throws Exception {
    Post otherUsersPost = createPost(otherUser, "Other title", Post.Status.created, null);

    mockMvc
        .perform(
            delete("/api/posts/{postId}", otherUsersPost.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            delete("/api/posts/{postId}", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());

    assertThat(postRepository.findById(otherUsersPost.getId()).orElseThrow().getDeletedAt())
        .isNull();
  }

  @Test
  void deletePost_adminCanDeleteAnotherUsersPostAndActionIsLogged() throws Exception {
    User admin = createUser("adminuser", "admin@example.com", User.Role.ADMIN);
    Post otherUsersPost = createPost(otherUser, "Other title", Post.Status.published, null);

    mockMvc
        .perform(
            delete("/api/posts/{postId}", otherUsersPost.getId())
                .header("Authorization", "Bearer " + jwtTokenService.generateAccessToken(admin)))
        .andExpect(status().isOk());

    assertThat(postRepository.findById(otherUsersPost.getId()).orElseThrow().getDeletedAt())
        .isNotNull();
    assertThat(moderationLogRepository.findAll())
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getActorId()).isEqualTo(admin.getId());
              assertThat(log.getAction()).isEqualTo("DELETE_POST");
              assertThat(log.getTargetId()).isEqualTo(otherUsersPost.getId());
            });
  }

  @Test
  void updateVisibility_adminHidesAndUnhidesPostAndActionsAreLogged() throws Exception {
    User admin = createUser("adminuser", "admin@example.com", User.Role.ADMIN);
    String adminToken = jwtTokenService.generateAccessToken(admin);
    Post published = createPost(user, "Published", Post.Status.published, null);
    published.setPublishedAt(Instant.now());
    postRepository.saveAndFlush(published);

    mockMvc
        .perform(
            patch("/api/posts/{postId}/visibility", published.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Post hidden successfully"))
        .andExpect(jsonPath("$.data.status").value("hidden"))
        .andExpect(jsonPath("$.data.publishedAt").doesNotExist());

    // the owner cannot bring a hidden post back
    mockMvc
        .perform(
            patch("/api/posts/{postId}", published.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Post content\",\"rawContent\":{},\"publish\":true}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            patch("/api/posts/{postId}/visibility", published.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("created"));

    assertThat(moderationLogRepository.findAll())
        .extracting(log -> log.getAction())
        .containsExactlyInAnyOrder("HIDE_POST", "UNHIDE_POST");
  }

  @Test
  void updateVisibility_nonAdminReturns403AndWithoutJwtReturns401() throws Exception {
    Post existingPost = createPost(user, "Mine", Post.Status.published, null);

    mockMvc
        .perform(
            patch("/api/posts/{postId}/visibility", existingPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\":true}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/posts/{postId}/visibility", existingPost.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\":true}"))
        .andExpect(status().isUnauthorized());

    assertThat(postRepository.findById(existingPost.getId()).orElseThrow().getStatus())
        .isEqualTo(Post.Status.published);
  }

  @Test
  void updateVisibility_invalidBodyMissingPostAndNotHiddenPost() throws Exception {
    User admin = createUser("adminuser", "admin@example.com", User.Role.ADMIN);
    String adminToken = jwtTokenService.generateAccessToken(admin);
    Post existingPost = createPost(user, "Mine", Post.Status.published, null);

    mockMvc
        .perform(
            patch("/api/posts/{postId}/visibility", existingPost.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            patch("/api/posts/{postId}/visibility", existingPost.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Post is not hidden"));
    mockMvc
        .perform(
            patch("/api/posts/{postId}/visibility", UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\":true}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void listPostsOfUser_guestSeesOnlyPublishedPostsOfThatUser() throws Exception {
    createPost(user, "Draft", Post.Status.created, null);
    createPost(user, "Hidden", Post.Status.hidden, null);
    createPost(user, "Deleted", Post.Status.published, Instant.now());
    createPost(user, "Published", Post.Status.published, null);
    createPost(otherUser, "Someone else", Post.Status.published, null);

    mockMvc
        .perform(get("/api/users/{userId}/posts", user.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Published"));
  }

  @Test
  void listPostsOfUser_otherMemberSeesOnlyPublished_ownerAndAdminSeeAll() throws Exception {
    createPost(user, "Draft", Post.Status.created, null);
    createPost(user, "Published", Post.Status.published, null);
    User admin = createUser("adminuser", "admin@example.com", User.Role.ADMIN);

    mockMvc
        .perform(
            get("/api/users/{userId}/posts", user.getId())
                .header(
                    "Authorization", "Bearer " + jwtTokenService.generateAccessToken(otherUser)))
        .andExpect(jsonPath("$.data.totalElements").value(1));
    mockMvc
        .perform(
            get("/api/users/{userId}/posts", user.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.data.totalElements").value(2));
    mockMvc
        .perform(
            get("/api/users/{userId}/posts", user.getId())
                .header("Authorization", "Bearer " + jwtTokenService.generateAccessToken(admin)))
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void listPostsOfUser_unknownUserReturns404_invalidPageReturns400() throws Exception {
    mockMvc
        .perform(get("/api/users/{userId}/posts", UUID.randomUUID()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/users/{userId}/posts", user.getId()).param("size", "0"))
        .andExpect(status().isBadRequest());
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

  @Test
  void listFeed_guestSeesOnlyPublishedPostsNewestFirst() throws Exception {
    createPost(user, "Draft", Post.Status.created, null);
    createPost(user, "Hidden", Post.Status.hidden, null);
    createPost(user, "Deleted", Post.Status.published, Instant.now());
    createPublished(user, "Older", Instant.parse("2026-01-01T00:00:00Z"));
    createPublished(otherUser, "Newer", Instant.parse("2026-02-01T00:00:00Z"));

    mockMvc
        .perform(get("/api/posts/feed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Posts retrieved successfully"))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.content[0].title").value("Newer"))
        .andExpect(jsonPath("$.data.content[1].title").value("Older"));
  }

  @Test
  void listFeed_memberSeesSamePublishedPostsAsGuest() throws Exception {
    createPost(user, "Draft", Post.Status.created, null);
    createPost(user, "Published", Post.Status.published, null);

    mockMvc
        .perform(get("/api/posts/feed").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Published"));
  }

  @Test
  void listFeed_invalidTokenReturns401() throws Exception {
    mockMvc
        .perform(get("/api/posts/feed").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listFeed_appliesPagination_invalidSizeReturns400() throws Exception {
    createPublished(user, "First", Instant.parse("2026-01-01T00:00:00Z"));
    createPublished(user, "Second", Instant.parse("2026-01-02T00:00:00Z"));
    createPublished(user, "Third", Instant.parse("2026-01-03T00:00:00Z"));

    mockMvc
        .perform(get("/api/posts/feed").param("page", "0").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.content[0].title").value("Third"));

    mockMvc.perform(get("/api/posts/feed").param("size", "0")).andExpect(status().isBadRequest());
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
            .rawContent(JsonNodeFactory.instance.objectNode())
            .status(status)
            .viewCount(0)
            .deletedAt(deletedAt)
            .build());
  }

  private Post createPublished(User owner, String title, Instant publishedAt) {
    Post post = createPost(owner, title, Post.Status.published, null);
    post.setPublishedAt(publishedAt);
    return postRepository.save(post);
  }
}
