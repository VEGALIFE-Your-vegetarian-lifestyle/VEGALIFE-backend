package com.vegalife.integration.controller.search;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import java.time.Instant;
import java.util.Set;
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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class SearchControllerIntegrationTest {

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

  private User user;

  @BeforeEach
  void setUp() {
    user =
        userRepository.save(
            User.builder()
                .username("author")
                .email("author@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
  }

  @Test
  void search_withoutJwt_isAllowed() throws Exception {
    mockMvc
        .perform(get("/api/search").param("q", "tofu"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void search_missingQueryReturns400() throws Exception {
    mockMvc.perform(get("/api/search")).andExpect(status().isBadRequest());
  }

  @Test
  void search_noMatchesReturnsEmptyPageWith200() throws Exception {
    mockMvc
        .perform(get("/api/search").param("q", "nonexistentkeyword"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void search_matchesPostsAndRecipesByKeyword() throws Exception {
    Post post = createPublishedPost("Vegan tofu bowl", "A simple plant-based lunch.");
    Recipe recipe = createRecipe("Tofu stir fry", "Fried tofu with vegetables.");

    mockMvc
        .perform(get("/api/search").param("q", "tofu"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(
            jsonPath("$.data.content[*].id")
                .value(containsInAnyOrder(post.getId().toString(), recipe.getId().toString())));
  }

  @Test
  void search_titleMatchRanksAboveBodyOnlyMatch() throws Exception {
    createPublishedPost("Easy weekday lunch", "Made with tofu and greens.");
    Post titleMatch = createPublishedPost("Vegan tofu bowl", "A simple lunch.");

    mockMvc
        .perform(get("/api/search").param("q", "tofu"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].id").value(titleMatch.getId().toString()));
  }

  @Test
  void search_draftPostIsNeverReturned() throws Exception {
    createPost("Tofu draft", "Not published yet.", Post.Status.created);

    mockMvc
        .perform(get("/api/search").param("q", "tofu"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty());
  }

  @Test
  void search_typeFilterVideoReturnsOnlyPostsWithVideoMedia() throws Exception {
    Post videoPost = createPublishedPost("Tofu cooking video", "Watch and learn.");
    Media video =
        mediaRepository.save(
            Media.builder().mimeType("video/mp4").status(Media.Status.succeed).build());
    videoPost.setMedia(new java.util.HashSet<>(Set.of(video)));
    postRepository.save(videoPost);
    createPublishedPost("Tofu written guide", "Read and learn.");

    mockMvc
        .perform(get("/api/search").param("q", "tofu").param("type", "video"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(videoPost.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].type").value("video"));
  }

  @Test
  void search_categoryFilterNarrowsPostsButNeverExcludesRecipes() throws Exception {
    Category matching = categoryRepository.save(Category.builder().name("Lunch").build());
    Category other = categoryRepository.save(Category.builder().name("Dinner").build());
    Post matchingPost = createPublishedPostWithCategory("Tofu lunch bowl", "Body.", matching);
    createPublishedPostWithCategory("Tofu dinner plate", "Body.", other);
    Recipe recipe = createRecipe("Tofu stir fry", "Fried tofu.");

    mockMvc
        .perform(
            get("/api/search").param("q", "tofu").param("categoryId", matching.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(
            jsonPath("$.data.content[*].id")
                .value(
                    containsInAnyOrder(
                        matchingPost.getId().toString(), recipe.getId().toString())));
  }

  @Test
  void search_invalidTypeReturns400() throws Exception {
    mockMvc
        .perform(get("/api/search").param("q", "tofu").param("type", "spaceship"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void search_snippetHighlightsMatchedText() throws Exception {
    createPublishedPost("Vegan tofu bowl", "A simple plant-based lunch with tofu inside.");

    mockMvc
        .perform(get("/api/search").param("q", "tofu"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.content[0].snippet")
                .value(org.hamcrest.Matchers.containsString("<mark>")));
  }

  @Test
  void search_pendingRecipeIsNeverReturned() throws Exception {
    // A recipe created by a member starts PENDING; search is public, so it must not be discoverable
    // through its instructions before an admin has reviewed it (ADR-016).
    Recipe pending = createRecipe("Tofu stir fry", "Fried tofu with vegetables.");
    pending.setModerationStatus(ModerationStatus.PENDING);
    recipeRepository.saveAndFlush(pending);

    mockMvc
        .perform(get("/api/search").param("q", "tofu"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty());
  }

  private Post createPublishedPost(String title, String content) {
    return createPost(title, content, Post.Status.published);
  }

  private Post createPublishedPostWithCategory(String title, String content, Category category) {
    Post post =
        postRepository.save(
            Post.builder()
                .user(user)
                .title(title)
                .content(content)
                .rawContent(JsonNodeFactory.instance.objectNode())
                .status(Post.Status.published)
                .viewCount(0)
                .categories(new java.util.HashSet<>(Set.of(category)))
                .build());
    post.setPublishedAt(Instant.now());
    return postRepository.save(post);
  }

  private Post createPost(String title, String content, Post.Status status) {
    return postRepository.save(
        Post.builder()
            .user(user)
            .title(title)
            .content(content)
            .rawContent(JsonNodeFactory.instance.objectNode())
            .status(status)
            .viewCount(0)
            .build());
  }

  private Recipe createRecipe(String name, String instructions) {
    Dish dish =
        dishRepository.save(
            Dish.builder()
                .name("Dish " + UUID.randomUUID())
                .moderationStatus(ModerationStatus.APPROVED)
                .build());
    // These fixtures stand for recipes that predate the approval rule, which the migration
    // backfilled to APPROVED.
    return recipeRepository.save(
        Recipe.builder()
            .dish(dish)
            .user(user)
            .name(name)
            .instructions(instructions)
            .servings(2)
            .moderationStatus(ModerationStatus.APPROVED)
            .build());
  }
}
