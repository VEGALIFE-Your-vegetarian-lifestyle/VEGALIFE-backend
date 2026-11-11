package com.vegalife.integration.controller.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.saved.SavedItemRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The saved-items surface end to end (BR-SAVED-001, BR-SAVED-002): saving a post and a recipe,
 * listing them back, unsaving, and the visibility rules that decide what a list omits.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class SavedItemControllerIntegrationTest {

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

  @Autowired private DishRepository dishRepository;

  @Autowired private RecipeRepository recipeRepository;

  @Autowired private SavedItemRepository savedItemRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User user;
  private User otherUser;
  private String accessToken;

  @BeforeEach
  void setUp() {
    savedItemRepository.deleteAll();
    recipeRepository.deleteAll();
    dishRepository.deleteAll();
    postRepository.deleteAll();
    userRepository.deleteAll();

    user = saveUser("saver", "saver@example.com");
    otherUser = saveUser("author", "author@example.com");
    accessToken = jwtTokenService.generateAccessToken(user);
  }

  @Test
  void savePost_publishedPostAppearsInSavedList() throws Exception {
    Post post = savePost(otherUser, PostStatus.published);

    mockMvc
        .perform(
            post("/api/saved/posts/{postId}", post.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(get("/api/saved/posts").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].id").value(post.getId().toString()))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void savePost_twiceStoresOneRow() throws Exception {
    Post post = savePost(otherUser, PostStatus.published);

    mockMvc
        .perform(
            post("/api/saved/posts/{postId}", post.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/saved/posts/{postId}", post.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());

    assertThat(savedItemRepository.findByUserIdAndPostId(user.getId(), post.getId())).isPresent();
    assertThat(savedItemRepository.count()).isEqualTo(1);
  }

  @Test
  void savePost_unpublishedPostReturns404() throws Exception {
    Post post = savePost(otherUser, PostStatus.created);

    mockMvc
        .perform(
            post("/api/saved/posts/{postId}", post.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Post not found"));
  }

  @Test
  void savePost_hiddenAfterSavingDisappearsFromList() throws Exception {
    Post post = savePost(otherUser, PostStatus.published);
    mockMvc.perform(
        post("/api/saved/posts/{postId}", post.getId())
            .header("Authorization", "Bearer " + accessToken));

    post.setStatus(PostStatus.unpublished);
    postRepository.saveAndFlush(post);

    mockMvc
        .perform(get("/api/saved/posts").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty());
  }

  @Test
  void unsavePost_removesItFromTheList() throws Exception {
    Post post = savePost(otherUser, PostStatus.published);
    mockMvc.perform(
        post("/api/saved/posts/{postId}", post.getId())
            .header("Authorization", "Bearer " + accessToken));

    mockMvc
        .perform(
            delete("/api/saved/posts/{postId}", post.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/saved/posts").header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.data.content").isEmpty());
  }

  @Test
  void unsavePost_whenNotSavedIsStillOk() throws Exception {
    mockMvc
        .perform(
            delete("/api/saved/posts/{postId}", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());
  }

  @Test
  void saveRecipe_approvedRecipeAppearsInSavedList() throws Exception {
    Recipe recipe = saveRecipe(otherUser, ModerationStatus.APPROVED);

    mockMvc
        .perform(
            post("/api/saved/recipes/{recipeId}", recipe.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/saved/recipes").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].id").value(recipe.getId().toString()))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void saveRecipe_pendingRecipeReturns404() throws Exception {
    Recipe recipe = saveRecipe(otherUser, ModerationStatus.PENDING);

    mockMvc
        .perform(
            post("/api/saved/recipes/{recipeId}", recipe.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Recipe not found"));
  }

  @Test
  void saveRecipe_demotedAfterSavingDisappearsFromList() throws Exception {
    Recipe recipe = saveRecipe(otherUser, ModerationStatus.APPROVED);
    mockMvc.perform(
        post("/api/saved/recipes/{recipeId}", recipe.getId())
            .header("Authorization", "Bearer " + accessToken));

    recipe.setModerationStatus(ModerationStatus.REJECTED);
    recipeRepository.saveAndFlush(recipe);

    mockMvc
        .perform(get("/api/saved/recipes").header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.data.content").isEmpty());
  }

  @Test
  void savedLists_arePrivateToTheCaller() throws Exception {
    Post post = savePost(otherUser, PostStatus.published);
    mockMvc.perform(
        post("/api/saved/posts/{postId}", post.getId())
            .header("Authorization", "Bearer " + accessToken));

    String otherToken = jwtTokenService.generateAccessToken(otherUser);

    mockMvc
        .perform(get("/api/saved/posts").header("Authorization", "Bearer " + otherToken))
        .andExpect(jsonPath("$.data.content").isEmpty());
  }

  @Test
  void savingWithoutAuthenticationReturns401() throws Exception {
    Post post = savePost(otherUser, PostStatus.published);

    mockMvc
        .perform(post("/api/saved/posts/{postId}", post.getId()))
        .andExpect(status().isUnauthorized());
  }

  private User saveUser(String displayName, String email) {
    return userRepository.save(
        User.builder()
            .displayName(displayName)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(UserRole.USER)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build());
  }

  private Post savePost(User owner, PostStatus status) {
    return postRepository.saveAndFlush(
        Post.builder()
            .user(owner)
            .title("A post")
            .content("Post body")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .status(status)
            .viewCount(0)
            .build());
  }

  private Recipe saveRecipe(User owner, ModerationStatus status) {
    Dish dish = dishRepository.save(Dish.builder().name("saved-dish-" + UUID.randomUUID()).build());
    return recipeRepository.saveAndFlush(
        Recipe.builder()
            .user(owner)
            .dish(dish)
            .name("Saved recipe")
            .instructions("Cook it")
            .servings(2)
            .moderationStatus(status)
            .build());
  }
}
