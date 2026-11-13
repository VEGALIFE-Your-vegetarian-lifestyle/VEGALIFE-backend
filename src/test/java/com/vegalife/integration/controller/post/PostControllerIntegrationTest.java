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
import com.vegalife.enums.outbound.OutboundChannel;
import com.vegalife.enums.outbound.OutboundStatus;
import com.vegalife.enums.post.MediaStatus;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@TestPropertySource(properties = "app.filter.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class PostControllerIntegrationTest {

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

  @Autowired private UserRepository userRepository;

  @Autowired private PostRepository postRepository;

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private MediaRepository mediaRepository;

  @Autowired private RecipeRepository recipeRepository;

  @Autowired private DishRepository dishRepository;

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
    Post published = createPost(otherUser, "Someone else's post", PostStatus.published, null);

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
  void getPost_ownNonPublishedPostIsReturned() throws Exception {
    Post draft = createPost(user, "My draft", PostStatus.drafted, null);

    mockMvc
        .perform(
            get("/api/posts/{postId}", draft.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(draft.getId().toString()))
        .andExpect(jsonPath("$.data.title").value("My draft"))
        .andExpect(jsonPath("$.data.status").value("drafted"));
  }

  @Test
  void getPost_softDeletedPostReturns404() throws Exception {
    Post deleted = createPost(user, "Gone", PostStatus.published, Instant.now());

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
    Post published = createPost(otherUser, "Public post", PostStatus.published, null);

    mockMvc
        .perform(get("/api/posts/{postId}", published.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(published.getId().toString()));
  }

  @Test
  void getPost_withoutJwt_nonPublishedPostIsReturned() throws Exception {
    Post draft = createPost(user, "My draft", PostStatus.drafted, null);

    mockMvc
        .perform(get("/api/posts/{postId}", draft.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(draft.getId().toString()))
        .andExpect(jsonPath("$.data.status").value("drafted"));
  }

  @Test
  void getPost_withoutJwt_softDeletedPostReturns404() throws Exception {
    Post deleted = createPost(user, "Gone", PostStatus.published, Instant.now());

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
        .andExpect(jsonPath("$.data.status").value("drafted"))
        .andExpect(jsonPath("$.data.viewCount").value(0));

    Post createdPost = postRepository.findAll().getFirst();
    assertThat(createdPost.getUser().getId()).isEqualTo(user.getId());
    assertThat(createdPost.getStatus()).isEqualTo(PostStatus.drafted);
    assertThat(createdPost.getPublishedAt()).isNull();
  }

  @Test
  void attachMedia_ownerAttachesAndGetPostReflectsIt() throws Exception {
    Post ownedPost = createPost(user, "My post", PostStatus.drafted, null);
    Media media = createMedia(MediaStatus.succeed);

    mockMvc
        .perform(
            post("/api/posts/{postId}/media", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\": [\"" + media.getId() + "\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Media attached successfully"))
        .andExpect(jsonPath("$.data.mediaIds", containsInAnyOrder(media.getId().toString())));

    mockMvc
        .perform(
            get("/api/posts/{postId}", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.data.mediaIds", containsInAnyOrder(media.getId().toString())));
  }

  @Test
  void attachMedia_reCallIsIdempotentAndEmptyListDetachesAll() throws Exception {
    Post ownedPost = createPost(user, "My post", PostStatus.drafted, null);
    Media media = createMedia(MediaStatus.succeed);
    mockMvc
        .perform(
            post("/api/posts/{postId}/media", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\": [\"" + media.getId() + "\"]}"))
        .andExpect(status().isOk());

    // re-calling with the same set is idempotent
    mockMvc
        .perform(
            post("/api/posts/{postId}/media", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\": [\"" + media.getId() + "\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mediaIds", containsInAnyOrder(media.getId().toString())));

    // empty list detaches everything
    mockMvc
        .perform(
            post("/api/posts/{postId}/media", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\": []}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mediaIds").isEmpty());
  }

  @Test
  void attachMedia_unsucceededMediaReturns400AndWritesNothing() throws Exception {
    Post ownedPost = createPost(user, "My post", PostStatus.drafted, null);
    Media uploading = createMedia(MediaStatus.uploading);

    mockMvc
        .perform(
            post("/api/posts/{postId}/media", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\": [\"" + uploading.getId() + "\"]}"))
        .andExpect(status().isBadRequest());

    assertThat(postRepository.findById(ownedPost.getId()).orElseThrow().getMedia()).isEmpty();
  }

  @Test
  void attachMedia_unknownMediaIdReturns400() throws Exception {
    Post ownedPost = createPost(user, "My post", PostStatus.drafted, null);

    mockMvc
        .perform(
            post("/api/posts/{postId}/media", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\": [\"" + UUID.randomUUID() + "\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void attachMedia_nonOwnerNonAdminGetsNotFound() throws Exception {
    Post othersPost = createPost(otherUser, "Someone else's post", PostStatus.drafted, null);
    Media media = createMedia(MediaStatus.succeed);

    mockMvc
        .perform(
            post("/api/posts/{postId}/media", othersPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\": [\"" + media.getId() + "\"]}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void attachRecipes_ownerAttachesAndGetPostReflectsIt() throws Exception {
    Post ownedPost = createPost(user, "My post", PostStatus.drafted, null);
    Recipe recipe = createRecipe(user);

    mockMvc
        .perform(
            post("/api/posts/{postId}/recipes", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipeIds\": [\"" + recipe.getId() + "\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Recipes attached successfully"))
        .andExpect(jsonPath("$.data.recipeIds", containsInAnyOrder(recipe.getId().toString())));

    mockMvc
        .perform(
            get("/api/posts/{postId}", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.data.recipeIds", containsInAnyOrder(recipe.getId().toString())));
  }

  @Test
  void attachRecipes_replacingSetDetachesRecipesNotInNewSet() throws Exception {
    Post ownedPost = createPost(user, "My post", PostStatus.drafted, null);
    Recipe first = createRecipe(user);
    Recipe second = createRecipe(user);
    mockMvc
        .perform(
            post("/api/posts/{postId}/recipes", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"recipeIds\": [\"" + first.getId() + "\", \"" + second.getId() + "\"]}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/posts/{postId}/recipes", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipeIds\": [\"" + first.getId() + "\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recipeIds", containsInAnyOrder(first.getId().toString())));
  }

  @Test
  void attachRecipes_emptyListDetachesAll() throws Exception {
    Post ownedPost = createPost(user, "My post", PostStatus.drafted, null);
    Recipe recipe = createRecipe(user);
    mockMvc
        .perform(
            post("/api/posts/{postId}/recipes", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipeIds\": [\"" + recipe.getId() + "\"]}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/posts/{postId}/recipes", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipeIds\": []}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recipeIds").isEmpty());
  }

  @Test
  void attachRecipes_unknownRecipeIdReturns404AndWritesNothing() throws Exception {
    Post ownedPost = createPost(user, "My post", PostStatus.drafted, null);

    mockMvc
        .perform(
            post("/api/posts/{postId}/recipes", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipeIds\": [\"" + UUID.randomUUID() + "\"]}"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get("/api/posts/{postId}", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.data.recipeIds").isEmpty());
  }

  @Test
  void attachRecipes_pendingRecipeReturns404AndWritesNothing() throws Exception {
    Post ownedPost = createPost(user, "My post", PostStatus.drafted, null);
    Dish dish =
        dishRepository.save(Dish.builder().name("Pending bowl " + UUID.randomUUID()).build());
    Recipe pending =
        recipeRepository.save(
            Recipe.builder()
                .dish(dish)
                .user(user)
                .name("Unreviewed bowl")
                .instructions("Cook it.")
                .servings(2)
                .moderationStatus(ModerationStatus.PENDING)
                .build());

    mockMvc
        .perform(
            post("/api/posts/{postId}/recipes", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipeIds\": [\"" + pending.getId() + "\"]}"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.message").value("One or more recipes are not found or are not approved"));

    mockMvc
        .perform(
            get("/api/posts/{postId}", ownedPost.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.data.recipeIds").isEmpty());
  }

  @Test
  void attachRecipes_nonOwnerNonAdminGetsNotFound() throws Exception {
    Post othersPost = createPost(otherUser, "Someone else's post", PostStatus.drafted, null);
    Recipe recipe = createRecipe(otherUser);

    mockMvc
        .perform(
            post("/api/posts/{postId}/recipes", othersPost.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipeIds\": [\"" + recipe.getId() + "\"]}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void attachMedia_adminCanAttachToAnotherUsersPost() throws Exception {
    User admin = createUser("adminuser", "admin@example.com", UserRole.ADMIN);
    String adminToken = jwtTokenService.generateAccessToken(admin);
    Post othersPost = createPost(otherUser, "Someone else's post", PostStatus.drafted, null);
    Media media = createMedia(MediaStatus.succeed);

    mockMvc
        .perform(
            post("/api/posts/{postId}/media", othersPost.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\": [\"" + media.getId() + "\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mediaIds", containsInAnyOrder(media.getId().toString())));
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
        .andExpect(jsonPath("$.data.status").value("processing"))
        .andExpect(jsonPath("$.data.flag").doesNotExist())
        .andExpect(jsonPath("$.data.categoryIds[0]").value(category.getId().toString()));

    Post queuedPost = postRepository.findAll().getFirst();
    assertThat(queuedPost.getStatus()).isEqualTo(PostStatus.processing);
    assertThat(queuedPost.getFlag()).isNull();
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
  void createPost_withBlankTitle_returnsFieldLevelError() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"content\":\"Test content\",\"rawContent\":{}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.data.title").isNotEmpty());
  }

  @Test
  void listFeed_withInvalidSize_returnsFieldLevelError() throws Exception {
    mockMvc
        .perform(get("/api/posts").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.data.size").isNotEmpty());
  }

  @Test
  void getPost_withMalformedId_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/posts/{postId}", "not-a-uuid")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createPost_withMalformedJson_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":"))
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
                .content("{\"title\":\" \",\"content\":\"Test content\",\"rawContent\":{}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void updatePost_withOneField_updatesOnlyThatField() throws Exception {
    Post existingPost = createPost(user, "Old title", PostStatus.published, null);
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
        .andExpect(jsonPath("$.data.status").value("processing"))
        .andExpect(jsonPath("$.data.viewCount").value(12));

    Post updatedPost = postRepository.findById(existingPost.getId()).orElseThrow();
    assertThat(updatedPost.getUser().getId()).isEqualTo(user.getId());
    // A content edit on a published post re-checks it, so it is out of the feed while in flight.
    assertThat(updatedPost.getStatus()).isEqualTo(PostStatus.processing);
    assertThat(updatedPost.getFlag()).isNull();
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
    Post existingPost = createPost(user, "Post title", PostStatus.drafted, null);

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
    Post existingPost = createPost(user, "Old title", PostStatus.drafted, null);
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
    Post deletedPost = createPost(user, "Deleted post", PostStatus.drafted, Instant.now());
    Post otherUsersPost = createPost(otherUser, "Other user's post", PostStatus.drafted, null);
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
    Post existingPost = createPost(user, "Post title", PostStatus.drafted, null);
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
        .andExpect(jsonPath("$.data.status").value("processing"))
        .andExpect(jsonPath("$.data.flag").doesNotExist())
        .andExpect(jsonPath("$.data.categoryIds[0]").value(category.getId().toString()));

    Post queuedPost = postRepository.findById(existingPost.getId()).orElseThrow();
    assertThat(queuedPost.getStatus()).isEqualTo(PostStatus.processing);
    assertThat(queuedPost.getFlag()).isNull();
    assertThat(queuedPost.getPublishedAt()).isNull();
  }

  @Test
  void updatePost_publishWithoutCategoryReturns400() throws Exception {
    Post existingPost = createPost(user, "Post title", PostStatus.drafted, null);

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
    User admin = createUser("adminuser", "admin@example.com", UserRole.ADMIN);
    Post otherUsersPost = createPost(otherUser, "Other title", PostStatus.drafted, null);

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
    Post existingPost = createPost(user, "Post title", PostStatus.drafted, null);

    mockMvc
        .perform(
            delete("/api/posts/{postId}", existingPost.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Post deleted successfully"));

    assertThat(postRepository.findById(existingPost.getId()).orElseThrow().getDeletedAt())
        .isNotNull();
    mockMvc
        .perform(get("/api/posts/me").header("Authorization", "Bearer " + accessToken))
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
    Post otherUsersPost = createPost(otherUser, "Other title", PostStatus.drafted, null);

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
    User admin = createUser("adminuser", "admin@example.com", UserRole.ADMIN);
    Post otherUsersPost = createPost(otherUser, "Other title", PostStatus.published, null);

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
  void updateVisibilityEndpoint_noLongerExists() throws Exception {
    User admin = createUser("adminuser", "admin@example.com", UserRole.ADMIN);
    String adminToken = jwtTokenService.generateAccessToken(admin);

    // ADR-023 removed admin hide/unhide: withdrawing is the author's unlist, and the admin's
    // terminal action is reject (unpublished). The route must not exist at all.
    for (PostStatus status : List.of(PostStatus.published, PostStatus.unpublished)) {
      Post post = createPost(user, "Post " + status, status, null);
      mockMvc
          .perform(
              patch("/api/posts/{postId}/visibility", post.getId())
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"hidden\":true}"))
          .andExpect(status().isNotFound());

      assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus()).isEqualTo(status);
    }
  }

  @Test
  void listPostsOfUser_guestSeesOnlyPublishedPostsOfThatUser() throws Exception {
    createPost(user, "Draft", PostStatus.drafted, null);
    createPost(user, "Held", PostStatus.processing, null);
    createPost(user, "Deleted", PostStatus.published, Instant.now());
    createPost(user, "Published", PostStatus.published, null);
    createPost(otherUser, "Someone else", PostStatus.published, null);

    mockMvc
        .perform(get("/api/users/{userId}/posts", user.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Published"));
  }

  @Test
  void listPostsOfUser_otherMemberSeesOnlyPublished_ownerAndAdminSeeAll() throws Exception {
    createPost(user, "Draft", PostStatus.drafted, null);
    createPost(user, "Published", PostStatus.published, null);
    User admin = createUser("adminuser", "admin@example.com", UserRole.ADMIN);

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
    createPost(user, "Published post", PostStatus.published, null);
    createPost(user, "Held post", PostStatus.processing, null);
    createPost(user, "Deleted post", PostStatus.published, Instant.now());
    createPost(otherUser, "Another user's post", PostStatus.published, null);

    mockMvc
        .perform(get("/api/posts/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Posts retrieved successfully"))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(
            jsonPath("$.data.content[*].title")
                .value(containsInAnyOrder("Published post", "Held post")))
        .andExpect(
            jsonPath("$.data.content[*].status")
                .value(containsInAnyOrder("published", "processing")));
  }

  @Test
  void listUserPosts_appliesPagination() throws Exception {
    createPost(user, "First post", PostStatus.drafted, null);
    createPost(user, "Second post", PostStatus.published, null);
    createPost(user, "Third post", PostStatus.unpublished, null);

    mockMvc
        .perform(
            get("/api/posts/me")
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
    mockMvc.perform(get("/api/posts/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void listUserPosts_withInvalidPageSize_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/posts/me")
                .header("Authorization", "Bearer " + accessToken)
                .param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listFeed_guestSeesOnlyPublishedPostsNewestFirst() throws Exception {
    createPost(user, "Draft", PostStatus.drafted, null);
    createPost(user, "Held", PostStatus.processing, null);
    createPost(user, "Deleted", PostStatus.published, Instant.now());
    createPublished(user, "Older", Instant.parse("2026-01-01T00:00:00Z"));
    createPublished(otherUser, "Newer", Instant.parse("2026-02-01T00:00:00Z"));

    mockMvc
        .perform(get("/api/posts"))
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
    createPost(user, "Draft", PostStatus.drafted, null);
    createPost(user, "Published", PostStatus.published, null);

    mockMvc
        .perform(get("/api/posts").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Published"));
  }

  @Test
  void listFeed_invalidTokenReturns401() throws Exception {
    mockMvc
        .perform(get("/api/posts").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listFeed_appliesPagination_invalidSizeReturns400() throws Exception {
    createPublished(user, "First", Instant.parse("2026-01-01T00:00:00Z"));
    createPublished(user, "Second", Instant.parse("2026-01-02T00:00:00Z"));
    createPublished(user, "Third", Instant.parse("2026-01-03T00:00:00Z"));

    mockMvc
        .perform(get("/api/posts").param("page", "0").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.content[0].title").value("Third"));

    mockMvc.perform(get("/api/posts").param("size", "0")).andExpect(status().isBadRequest());
  }

  @Test
  void getPost_authorSeesTheirOwnRejectionReason() throws Exception {
    Post rejected = createRejectedPost(user, "My rejected post", "promotes a competing platform");

    mockMvc
        .perform(
            get("/api/posts/{postId}", rejected.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rejectionReason").value("promotes a competing platform"));
  }

  @Test
  void getPost_guestDoesNotSeeTheRejectionReason() throws Exception {
    Post rejected = createRejectedPost(user, "My rejected post", "promotes a competing platform");

    mockMvc
        .perform(get("/api/posts/{postId}", rejected.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(rejected.getId().toString()))
        // The mapper must not populate this; only the owner may read it.
        .andExpect(jsonPath("$.data.rejectionReason").doesNotExist());
  }

  @Test
  void getPost_anotherMemberDoesNotSeeTheRejectionReason() throws Exception {
    Post rejected = createRejectedPost(user, "My rejected post", "promotes a competing platform");

    mockMvc
        .perform(
            get("/api/posts/{postId}", rejected.getId())
                .header(
                    "Authorization", "Bearer " + jwtTokenService.generateAccessToken(otherUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rejectionReason").doesNotExist());
  }

  @Test
  void listUserPosts_includesTheReasonForTheAuthorsOwnRejectedPost() throws Exception {
    createRejectedPost(user, "My rejected post", "off-topic for the platform");

    mockMvc
        .perform(get("/api/posts/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.content[0].rejectionReason").value("off-topic for the platform"));
  }

  @Test
  void listFeed_neverExposesARejectionReason() throws Exception {
    Post post = createPublished(user, "Withheld post", Instant.now());
    post.setRejectionReason("off-topic for the platform");
    postRepository.save(post);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].rejectionReason").doesNotExist());
  }

  private Post createRejectedPost(User owner, String title, String rejectionReason) {
    Post post = createPost(owner, title, PostStatus.unpublished, null);
    post.setRejectionReason(rejectionReason);
    return postRepository.save(post);
  }

  private User createUser(String displayName, String email) {
    return createUser(displayName, email, UserRole.USER);
  }

  private User createUser(String displayName, String email, UserRole role) {
    return userRepository.save(
        User.builder()
            .displayName(displayName)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(role)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build());
  }

  private Post createPost(User owner, String title, PostStatus status, Instant deletedAt) {
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
    Post post = createPost(owner, title, PostStatus.published, null);
    post.setPublishedAt(publishedAt);
    return postRepository.save(post);
  }

  private Media createMedia(MediaStatus status) {
    return mediaRepository.save(
        Media.builder().mediaUrl("https://example.com/video.mp4").status(status).build());
  }

  /**
   * An {@code APPROVED} recipe, because only an approved one may be attached to a post
   * (BR-POST-013). They are created directly through the repository rather than through {@code POST
   * /api/recipes}, which would leave them {@code PENDING} and so unusable here.
   */
  private Recipe createRecipe(User owner) {
    Dish dish = dishRepository.save(Dish.builder().name("Tofu bowl " + UUID.randomUUID()).build());
    return recipeRepository.save(
        Recipe.builder()
            .dish(dish)
            .user(owner)
            .name("Vegan tofu bowl")
            .instructions("Cook rice, add tofu.")
            .servings(2)
            .moderationStatus(ModerationStatus.APPROVED)
            .build());
  }
}
