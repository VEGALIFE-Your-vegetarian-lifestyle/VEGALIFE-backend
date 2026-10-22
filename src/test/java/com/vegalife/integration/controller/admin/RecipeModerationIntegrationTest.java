package com.vegalife.integration.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.model.recipe.ModerationStatus;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
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

/**
 * Recipe visibility and admin management, end to end against Postgres (ADR-016).
 *
 * <p>A member creates recipes and nothing else. This covers the read paths added with moderation
 * (own recipe, own list), the admin update/delete, and the two places a pending recipe must not
 * appear: another member's read and public search.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class RecipeModerationIntegrationTest {

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

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private PostRepository postRepository;

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private MediaRepository mediaRepository;

  @Autowired private ModerationLogRepository moderationLogRepository;

  @Autowired private RecipeRepository recipeRepository;

  @Autowired private RecipeIngredientRepository recipeIngredientRepository;

  @Autowired private DishRepository dishRepository;

  @Autowired private IngredientRepository ingredientRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private String adminToken;
  private String authorToken;
  private String otherMemberToken;

  @BeforeEach
  void setUp() {
    moderationLogRepository.deleteAll();
    recipeIngredientRepository.deleteAll();
    recipeRepository.deleteAll();
    dishRepository.deleteAll();
    ingredientRepository.deleteAll();
    postRepository.deleteAll();
    mediaRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    adminToken =
        jwtTokenService.generateAccessToken(
            createUser("adminrecipes", "admin.recipes@example.com", User.Role.ADMIN));
    authorToken =
        jwtTokenService.generateAccessToken(
            createUser("authorrecipes", "author.recipes@example.com", User.Role.USER));
    otherMemberToken =
        jwtTokenService.generateAccessToken(
            createUser("otherrecipes", "other.recipes@example.com", User.Role.USER));
  }

  @Test
  void createRecipe_startsPendingAlongWithItsDish() throws Exception {
    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + authorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(recipeBody()))
        .andExpect(status().isCreated())
        // The author is told why the recipe is not live yet, instead of it silently vanishing.
        .andExpect(jsonPath("$.data.moderationStatus").value("PENDING"))
        .andExpect(jsonPath("$.data.dishModerationStatus").value("PENDING"));
  }

  @Test
  void ownRecipe_isReadableAndListed() throws Exception {
    UUID recipeId = createRecipe();

    mockMvc
        .perform(get("/api/recipes/" + recipeId).header("Authorization", "Bearer " + authorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(recipeId.toString()))
        .andExpect(jsonPath("$.data.moderationStatus").value("PENDING"));

    mockMvc
        .perform(get("/api/recipes/me").header("Authorization", "Bearer " + authorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(recipeId.toString()));
  }

  @Test
  void anotherMembersRecipe_isReportedAsNotFound() throws Exception {
    UUID recipeId = createRecipe();

    // A pending recipe belongs to its author until an admin approves it.
    mockMvc
        .perform(
            get("/api/recipes/" + recipeId).header("Authorization", "Bearer " + otherMemberToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void pendingRecipe_isNotDiscoverableThroughSearch() throws Exception {
    createRecipe();

    mockMvc
        .perform(get("/api/search").param("q", "rainbow"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty());
  }

  @Test
  void approvingARecipe_makesItSearchable() throws Exception {
    UUID recipeId = createRecipe();

    mockMvc
        .perform(
            post("/api/admin/recipes/" + recipeId + "/approve")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.moderationStatus").value("APPROVED"));

    mockMvc
        .perform(get("/api/search").param("q", "rainbow"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].id").value(recipeId.toString()));
  }

  @Test
  void adminUpdate_changesTextButKeepsStateAndScore() throws Exception {
    UUID recipeId = createRecipe();
    Recipe recipe = recipeRepository.findById(recipeId).orElseThrow();
    recipe.setScore(0.31);
    recipe.setModerationStatus(ModerationStatus.PENDING);
    recipeRepository.saveAndFlush(recipe);

    mockMvc
        .perform(
            patch("/api/admin/recipes/" + recipeId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instructions\":\"Simmer for 20 minutes\",\"servings\":4}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.instructions").value("Simmer for 20 minutes"))
        .andExpect(jsonPath("$.data.servings").value(4))
        // The admin is the approver, so an edit is not a re-review...
        .andExpect(jsonPath("$.data.moderationStatus").value("PENDING"))
        // ...and the score still records why it was queued.
        .andExpect(jsonPath("$.data.score").value(0.31));
  }

  @Test
  void adminDelete_softDeletesAndRemovesItFromTheAuthorsList() throws Exception {
    UUID recipeId = createRecipe();

    mockMvc
        .perform(
            delete("/api/admin/recipes/" + recipeId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    assertThat(recipeRepository.findById(recipeId).orElseThrow().getDeletedAt()).isNotNull();
    mockMvc
        .perform(get("/api/recipes/me").header("Authorization", "Bearer " + authorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    mockMvc
        .perform(
            get("/api/admin/recipes/" + recipeId).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminList_canFilterToTheRecipeReviewQueue() throws Exception {
    createRecipe();

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("moderationStatus", "PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].moderationStatus").value("PENDING"))
        .andExpect(jsonPath("$.data.content[0].dishModerationStatus").value("PENDING"));
  }

  @Test
  void memberCannotUpdateOrDeleteARecipe() throws Exception {
    UUID recipeId = createRecipe();

    mockMvc
        .perform(
            patch("/api/admin/recipes/" + recipeId)
                .header("Authorization", "Bearer " + authorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instructions\":\"Rewrite it\"}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            delete("/api/admin/recipes/" + recipeId)
                .header("Authorization", "Bearer " + authorToken))
        .andExpect(status().isForbidden());
  }

  private UUID createRecipe() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/recipes")
                    .header("Authorization", "Bearer " + authorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(recipeBody()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode json = objectMapper.readTree(body);
    return UUID.fromString(json.path("data").path("id").asText());
  }

  private String recipeBody() {
    return """
        {
          "name": "Rainbow Bowl",
          "dishName": "rainbow bowl",
          "description": "Quick and tasty",
          "instructions": "Chop and cook",
          "prepTimeMinutes": 10,
          "cookTimeMinutes": 20,
          "servings": 2,
          "difficulty": "EASY",
          "ingredients": [{"name": "tofu", "amount": 150, "unit": "g"}]
        }
        """;
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
}
