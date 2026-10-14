package com.vegalife.integration.controller.search;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import org.junit.jupiter.api.BeforeEach;
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
  @Autowired private MediaRepository mediaRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private DishRepository dishRepository;
  @Autowired private RecipeRepository recipeRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private User owner;

  @BeforeEach
  void setUp() {
    owner =
        userRepository.save(
            User.builder()
                .username("searchowner")
                .email("searchowner@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
  }

  @Test
  void search_isPublicAndAggregatesPostsVideosAndRecipesWithHighlighting() throws Exception {
    Category category = categoryRepository.save(Category.builder().name("Vegan meals").build());
    Post published = createPublishedPost("vegan", "A vegan salad idea");
    jdbcTemplate.update(
        "INSERT INTO post_category(post_id, category_id) VALUES (?, ?)",
        published.getId(),
        category.getId());
    createVideo(published, "Vegan salad video");
    createRecipe(published, "Vegan salad recipe");

    mockMvc
        .perform(get("/api/search").param("q", "vegan").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(
            jsonPath("$.data.content[*].type", containsInAnyOrder("POST", "VIDEO", "RECIPE")));

    mockMvc
        .perform(get("/api/search").param("q", "vegan").param("type", "POST"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].score").value(1.0))
        .andExpect(jsonPath("$.data.content[0].highlightedTitle").value("<mark>vegan</mark>"));
  }

  @Test
  void search_appliesTypeDateCategoryAndGlobalPagination() throws Exception {
    Category selected = categoryRepository.save(Category.builder().name("Selected").build());
    Category other = categoryRepository.save(Category.builder().name("Other").build());
    Post included = createPublishedPost("Tofu dinner", "Dinner ideas", "2026-05-10T00:00:00Z");
    Post includedSecond =
        createPublishedPost("Tofu dinner two", "More dinner ideas", "2026-05-10T00:00:00Z");
    Post excludedCategory =
        createPublishedPost("Tofu lunch", "Lunch ideas", "2026-05-10T00:00:00Z");
    jdbcTemplate.update(
        "INSERT INTO post_category VALUES (?, ?)", included.getId(), selected.getId());
    jdbcTemplate.update(
        "INSERT INTO post_category VALUES (?, ?)", includedSecond.getId(), selected.getId());
    jdbcTemplate.update(
        "INSERT INTO post_category VALUES (?, ?)", excludedCategory.getId(), other.getId());
    createVideo(included, "Dinner video");
    createRecipe(included, "Dinner recipe");

    mockMvc
        .perform(
            get("/api/search")
                .param("q", "tofu")
                .param("categoryId", selected.getId().toString())
                .param("from", "2026-05-10T00:00:00Z")
                .param("to", "2026-05-10T00:00:00Z")
                .param("page", "0")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.page").value(0));

    mockMvc
        .perform(
            get("/api/search")
                .param("q", "dinner")
                .param("categoryId", selected.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(4));

    mockMvc
        .perform(get("/api/search").param("q", "tofu").param("type", "POST"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(3));
  }

  @Test
  void search_excludesNonPublishedDeletedAndUnattachedVideoContent() throws Exception {
    createPost("Draft vegan", "vegan hidden", Post.Status.created, null);
    createPost("Deleted vegan", "vegan hidden", Post.Status.published, Instant.now());
    Post publicPost = createPublishedPost("Public vegan plant", "vegan recipe");
    createVideo(publicPost, "Uploaded video");
    mediaRepository.save(
        Media.builder()
            .description("vegan unattached")
            .mimeType("video/mp4")
            .status(Media.Status.succeed)
            .build());
    Dish dish = dishRepository.save(Dish.builder().name("Vegan dish").build());
    recipeRepository.save(
        Recipe.builder()
            .dish(dish)
            .user(owner)
            .name("Deleted vegan recipe")
            .instructions("vegan instruction")
            .servings(1)
            .deletedAt(Instant.now())
            .build());

    mockMvc
        .perform(get("/api/search").param("q", "vegan"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[*].type", containsInAnyOrder("POST", "VIDEO")));
  }

  @Test
  void search_rejectsInvalidQueryAndDateRange() throws Exception {
    mockMvc.perform(get("/api/search").param("q", " ")).andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/search")
                .param("q", "food")
                .param("from", "2026-05-11T00:00:00Z")
                .param("to", "2026-05-10T00:00:00Z"))
        .andExpect(status().isBadRequest());
  }

  private Post createPublishedPost(String title, String content) {
    return createPublishedPost(title, content, "2026-05-10T00:00:00Z");
  }

  private Post createPublishedPost(String title, String content, String publishedAt) {
    Post post = createPost(title, content, Post.Status.published, null);
    post.setPublishedAt(Instant.parse(publishedAt));
    return postRepository.saveAndFlush(post);
  }

  private Post createPost(String title, String content, Post.Status status, Instant deletedAt) {
    return postRepository.save(
        Post.builder()
            .user(owner)
            .title(title)
            .content(content)
            .rawContent(JsonNodeFactory.instance.objectNode())
            .status(status)
            .viewCount(0)
            .deletedAt(deletedAt)
            .build());
  }

  private Media createVideo(Post post, String description) {
    Media media =
        mediaRepository.save(
            Media.builder()
                .description(description)
                .mimeType("video/mp4")
                .status(Media.Status.succeed)
                .uploadedBy(owner)
                .build());
    post.getMedia().add(media);
    postRepository.save(post);
    return media;
  }

  private Recipe createRecipe(Post post, String name) {
    Dish dish = dishRepository.save(Dish.builder().name("Salad dish").build());
    Recipe recipe =
        recipeRepository.save(
            Recipe.builder()
                .dish(dish)
                .user(owner)
                .name(name)
                .description("Fresh vegan ingredients")
                .instructions("Mix the ingredients")
                .servings(2)
                .posts(Set.of(post))
                .build());
    recipeRepository.flush();
    return recipe;
  }
}
