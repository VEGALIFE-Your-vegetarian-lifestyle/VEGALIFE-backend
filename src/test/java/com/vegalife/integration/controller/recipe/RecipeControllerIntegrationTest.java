package com.vegalife.integration.controller.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.enums.recipe.Difficulty;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.recipe.Unit;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.model.user.User;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
class RecipeControllerIntegrationTest {

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

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private DishRepository dishRepository;

  @Autowired private IngredientRepository ingredientRepository;

  @Autowired private RecipeRepository recipeRepository;

  @Autowired private RecipeIngredientRepository recipeIngredientRepository;

  private String accessToken;

  private User testUser;

  @BeforeEach
  void setUp() {
    User user =
        User.builder()
            .displayName("recipeuser")
            .email("recipe@example.com")
            .passwordHash("$2a$10$test")
            .role(UserRole.USER)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build();
    testUser = userRepository.save(user);
    accessToken = jwtTokenService.generateAccessToken(testUser);
  }

  @Test
  void createRecipe_validRequest_returns201WithNormalizedRowsPersisted() throws Exception {
    String requestJson =
        """
        {
          "name": "Rainbow Bowl",
          "dishName": "  Tofu Stir-Fry  ",
          "description": "Quick and tasty",
          "instructions": "Chop and cook",
          "prepTimeMinutes": 10,
          "cookTimeMinutes": 20,
          "servings": 2,
          "difficulty": "EASY",
          "ingredients": [
            {"name": "  Tofu ", "amount": 150, "unit": "g"},
            {"name": "Soy Sauce", "amount": 1.5, "unit": "ml"}
          ]
        }
        """;

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Recipe created successfully"))
        .andExpect(jsonPath("$.data.name").value("Rainbow Bowl"))
        .andExpect(jsonPath("$.data.dishName").value("tofu stir-fry"))
        .andExpect(jsonPath("$.data.difficulty").value("EASY"))
        .andExpect(jsonPath("$.data.servings").value(2))
        .andExpect(jsonPath("$.data.userId").isNotEmpty())
        .andExpect(jsonPath("$.data.dishId").isNotEmpty())
        .andExpect(jsonPath("$.data.ingredients.length()").value(2))
        .andExpect(jsonPath("$.data.ingredients[0].name").value("tofu"))
        .andExpect(jsonPath("$.data.ingredients[1].name").value("soy sauce"))
        // Freshly typed names are created uncurated, so the line reports unknown nutrition and the
        // recipe reports no total at all — never a partial or zero sum (BR-RECP-005/006).
        .andExpect(jsonPath("$.data.ingredients[0].unit").value("G"))
        .andExpect(jsonPath("$.data.ingredients[0].calories").value(nullValue()))
        .andExpect(jsonPath("$.data.ingredients[0].nutritionResolved").value(false))
        .andExpect(jsonPath("$.data.calories").value(nullValue()))
        .andExpect(jsonPath("$.data.nutritionResolved").value(false));

    Dish dish =
        dishRepository.findByNameIgnoreCaseAndDeletedAtIsNull("tofu stir-fry").orElseThrow();
    assertThat(dish).isNotNull();

    List<Recipe> recipes = recipeRepository.findAll();
    assertThat(recipes).hasSize(1);
    Recipe recipe = recipes.get(0);
    assertThat(recipe.getName()).isEqualTo("Rainbow Bowl");
    assertThat(recipe.getDish().getId()).isEqualTo(dish.getId());

    List<RecipeIngredient> links = recipeIngredientRepository.findByRecipeId(recipe.getId());
    assertThat(links).hasSize(2);
    RecipeIngredient tofuLink =
        links.stream()
            .filter(link -> link.getIngredientId().equals(ingredientIdOf("tofu")))
            .findFirst()
            .orElseThrow();
    assertThat(tofuLink.getAmount()).isEqualByComparingTo("150");

    // The unit lives on the ingredient now, not on the line; the member's "g" created it.
    Ingredient tofu = ingredientRepository.findById(tofuLink.getIngredientId()).orElseThrow();
    assertThat(tofu.getUnit()).isEqualTo(Unit.G);
    // No nutrition was entered, so no nutrition was invented.
    assertThat(recipe.getCalories()).isNull();
  }

  @Test
  void createRecipe_repeatedMixedCaseRequests_reuseSameDishAndIngredient() throws Exception {
    long baselineIngredients = ingredientRepository.count();
    String firstJson =
        """
        {
          "name": "Tofu Bowl One",
          "dishName": "tofu stir-fry",
          "instructions": "Cook it",
          "servings": 2,
          "ingredients": [
            {"name": "Tofu", "amount": 100, "unit": "g"}
          ]
        }
        """;
    String secondJson =
        """
        {
          "name": "Tofu Bowl Two",
          "dishName": "TOFU STIR-FRY",
          "instructions": "Cook it again",
          "servings": 4,
          "ingredients": [
            {"name": "TOFU", "amount": 200, "unit": "g"}
          ]
        }
        """;

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstJson))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.dishName").value("tofu stir-fry"))
        .andExpect(jsonPath("$.data.ingredients[0].name").value("tofu"));

    assertThat(dishRepository.count()).isEqualTo(1);
    // Delta, not an absolute: the V50 reference seed may already hold ingredients.
    assertThat(ingredientRepository.count()).isEqualTo(baselineIngredients + 1);
    assertThat(recipeRepository.count()).isEqualTo(2);
  }

  @Test
  void createRecipe_missingRequiredFields_returns400() throws Exception {
    String requestJson =
        """
        {
          "dishName": "tofu stir-fry",
          "ingredients": []
        }
        """;

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"));

    assertThat(recipeRepository.count()).isZero();
  }

  @Test
  void createRecipe_duplicateIngredientNames_returns400() throws Exception {
    String requestJson =
        """
        {
          "name": "Rainbow Bowl",
          "dishName": "tofu stir-fry",
          "instructions": "Cook it",
          "servings": 2,
          "ingredients": [
            {"name": "Tofu", "amount": 100, "unit": "g"},
            {"name": "  tofu ", "amount": 50, "unit": "g"}
          ]
        }
        """;

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Duplicate ingredient name: tofu"));

    assertThat(recipeRepository.count()).isZero();
  }

  @Test
  void createRecipe_invalidDifficulty_returns400() throws Exception {
    String requestJson =
        """
        {
          "name": "Rainbow Bowl",
          "dishName": "tofu stir-fry",
          "instructions": "Cook it",
          "servings": 2,
          "difficulty": "SPICY",
          "ingredients": [
            {"name": "Tofu", "amount": 100, "unit": "g"}
          ]
        }
        """;

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"));

    assertThat(recipeRepository.count()).isZero();
  }

  @Test
  void createRecipe_tooManyIngredients_returns400() throws Exception {
    String ingredientEntries =
        IntStream.range(0, 51)
            .mapToObj(
                i -> "{\"name\": \"ingredient %d\", \"amount\": 1, \"unit\": \"g\"}".formatted(i))
            .collect(Collectors.joining(", "));
    String requestJson =
        """
        {
          "name": "Rainbow Bowl",
          "dishName": "tofu stir-fry",
          "instructions": "Cook it",
          "servings": 2,
          "ingredients": [%s]
        }
        """
            .formatted(ingredientEntries);

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"));

    assertThat(recipeRepository.count()).isZero();
  }

  @Test
  void createRecipe_withoutJwt_returns401() throws Exception {
    String requestJson =
        """
        {
          "name": "Rainbow Bowl",
          "dishName": "tofu stir-fry",
          "instructions": "Cook it",
          "servings": 2,
          "ingredients": [
            {"name": "Tofu", "amount": 100, "unit": "g"}
          ]
        }
        """;

    mockMvc
        .perform(post("/api/recipes").contentType(MediaType.APPLICATION_JSON).content(requestJson))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getRecipe_returnsTheRejectionReasonToItsOwner() throws Exception {
    Recipe recipe = createRejectedRecipe("Rainbow Bowl", "instructions are off-topic");

    mockMvc
        .perform(
            get("/api/recipes/{recipeId}", recipe.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.moderationStatus").value("REJECTED"))
        .andExpect(jsonPath("$.data.rejectionReason").value("instructions are off-topic"));
  }

  @Test
  void listMyRecipes_includesTheRejectionReason() throws Exception {
    createRejectedRecipe("Rainbow Bowl", "instructions are off-topic");

    mockMvc
        .perform(get("/api/recipes/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.content[0].rejectionReason").value("instructions are off-topic"));
  }

  @Test
  void listMyRecipes_isPaginatedAndFiltersByModerationStatus() throws Exception {
    createRecipeWithStatus("Approved bowl", ModerationStatus.APPROVED);
    createRecipeWithStatus("Rejected bowl", ModerationStatus.REJECTED);

    mockMvc
        .perform(
            get("/api/recipes/me")
                .header("Authorization", "Bearer " + accessToken)
                .param("moderationStatus", "REJECTED")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.size").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Rejected bowl"));
  }

  @Test
  void listRecipes_keywordMatchesTextDishAndIngredientFields() throws Exception {
    createApprovedRecipe("Pho bowl", "Noodle soup", List.of());
    createApprovedRecipe("Weeknight dinner", "Pho noodle soup", List.of());
    createApprovedRecipe("Other bowl", "Rice bowl", List.of("pho spice"));
    createApprovedRecipe("Unrelated", "Salad", List.of("lettuce"));

    mockMvc
        .perform(get("/api/recipes").param("keyword", "PHO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(3));
  }

  @Test
  void listRecipes_keywordReturnsOneRowWhenSeveralIngredientLinesMatch() throws Exception {
    createApprovedRecipe("Bowl", "Rice", List.of("tofu", "smoked tofu"));
    createApprovedRecipe("Other", "Salad", List.of("lettuce"));

    mockMvc
        .perform(get("/api/recipes").param("keyword", "tofu"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1));
  }

  @Test
  void listRecipes_treatsKeywordWildcardsLiterally() throws Exception {
    createApprovedRecipe("100% vegan", "Bowl", List.of());
    createApprovedRecipe("Plain", "Salad", List.of());

    mockMvc
        .perform(get("/api/recipes").param("keyword", "%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("100% vegan"));
  }

  @Test
  void listRecipes_filtersByDishDifficultyAndTotalTime() throws Exception {
    Recipe quick = createApprovedRecipe("Quick soup", "Soup", Difficulty.EASY, 10, 10, List.of());
    createApprovedRecipe("Slow stew", "Stew", Difficulty.HARD, 60, 60, List.of());
    createApprovedRecipe("Unknown time", "Mystery", Difficulty.EASY, 10, null, List.of());

    mockMvc
        .perform(get("/api/recipes").param("difficulty", "EASY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));
    mockMvc
        .perform(get("/api/recipes").param("maxTotalTimeMinutes", "30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(quick.getId().toString()));
    mockMvc
        .perform(get("/api/recipes").param("dishId", quick.getDish().getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
    mockMvc
        .perform(get("/api/recipes").param("difficulty", "HARD").param("maxTotalTimeMinutes", "30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    mockMvc
        .perform(get("/api/recipes").param("difficulty", "IMPOSSIBLE"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteRecipe_ownRecipe_returns200AndRemovesItFromReadAndLists() throws Exception {
    Recipe recipe = createApprovedRecipe("Deletable", "Deletable Dish", List.of("tofu"));

    mockMvc
        .perform(
            delete("/api/recipes/{recipeId}", recipe.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Recipe deleted successfully"))
        .andExpect(jsonPath("$.data").value(nullValue()));

    assertThat(recipeRepository.findById(recipe.getId()).orElseThrow().getDeletedAt()).isNotNull();

    mockMvc
        .perform(get("/api/recipes/{recipeId}", recipe.getId()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/recipes/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void deleteRecipe_repeatDelete_returns404() throws Exception {
    Recipe recipe = createApprovedRecipe("Deleted Twice", "Deleted Dish", List.of("tofu"));

    mockMvc
        .perform(
            delete("/api/recipes/{recipeId}", recipe.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/api/recipes/{recipeId}", recipe.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteRecipe_anotherMembersRecipe_returns404AndKeepsIt() throws Exception {
    Recipe recipe = createApprovedRecipe("Someone Else's", "Shared Dish", List.of("tofu"));
    User other =
        userRepository.save(
            User.builder()
                .displayName("recipeother")
                .email("recipe-other@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.USER)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
    String otherToken = jwtTokenService.generateAccessToken(other);

    mockMvc
        .perform(
            delete("/api/recipes/{recipeId}", recipe.getId())
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());

    assertThat(recipeRepository.findById(recipe.getId()).orElseThrow().getDeletedAt()).isNull();
  }

  @Test
  void deleteRecipe_adminIsNotAnOverrideOnTheMemberRoute() throws Exception {
    Recipe recipe = createApprovedRecipe("Admin Cannot", "Admin Dish", List.of("tofu"));
    User admin =
        userRepository.save(
            User.builder()
                .displayName("recipeadmin")
                .email("recipe-admin@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.ADMIN)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
    String adminToken = jwtTokenService.generateAccessToken(admin);

    mockMvc
        .perform(
            delete("/api/recipes/{recipeId}", recipe.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());

    assertThat(recipeRepository.findById(recipe.getId()).orElseThrow().getDeletedAt()).isNull();
  }

  @Test
  void deleteRecipe_withoutToken_returns401() throws Exception {
    Recipe recipe = createApprovedRecipe("No Token", "No Token Dish", List.of("tofu"));

    mockMvc
        .perform(delete("/api/recipes/{recipeId}", recipe.getId()))
        .andExpect(status().isUnauthorized());
  }

  private Recipe createApprovedRecipe(String name, String dishName, List<String> ingredientNames) {
    return createApprovedRecipe(
        name, dishName, "Simple", "Cook it", null, null, null, ingredientNames);
  }

  private Recipe createApprovedRecipe(
      String name,
      String dishName,
      Difficulty difficulty,
      Integer prepTimeMinutes,
      Integer cookTimeMinutes,
      List<String> ingredientNames) {
    return createApprovedRecipe(
        name,
        dishName,
        "Simple",
        "Cook it",
        difficulty,
        prepTimeMinutes,
        cookTimeMinutes,
        ingredientNames);
  }

  private Recipe createApprovedRecipe(
      String name,
      String dishName,
      String description,
      String instructions,
      Difficulty difficulty,
      Integer prepTimeMinutes,
      Integer cookTimeMinutes,
      List<String> ingredientNames) {
    Dish dish =
        dishRepository.save(Dish.builder().name(dishName + " " + UUID.randomUUID()).build());
    Recipe recipe =
        recipeRepository.save(
            Recipe.builder()
                .user(testUser)
                .dish(dish)
                .name(name)
                .description(description)
                .instructions(instructions)
                .servings(2)
                .difficulty(difficulty)
                .prepTimeMinutes(prepTimeMinutes)
                .cookTimeMinutes(cookTimeMinutes)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());
    for (String ingredientName : ingredientNames) {
      Ingredient ingredient =
          ingredientRepository.save(
              Ingredient.builder().name(ingredientName + " " + UUID.randomUUID()).build());
      recipeIngredientRepository.save(
          RecipeIngredient.builder()
              .recipeId(recipe.getId())
              .ingredientId(ingredient.getId())
              .amount(BigDecimal.ONE)
              .build());
    }
    return recipe;
  }

  private Recipe createRecipeWithStatus(String name, ModerationStatus status) {
    Dish dish = dishRepository.save(Dish.builder().name("Dish " + UUID.randomUUID()).build());
    return recipeRepository.save(
        Recipe.builder()
            .user(testUser)
            .dish(dish)
            .name(name)
            .instructions("Cook it")
            .servings(2)
            .moderationStatus(status)
            .build());
  }

  private Recipe createRejectedRecipe(String name, String rejectionReason) {
    Dish dish = dishRepository.save(Dish.builder().name("tofu stir-fry " + name).build());
    Recipe recipe =
        Recipe.builder()
            .user(testUser)
            .dish(dish)
            .name(name)
            .instructions("Cook it")
            .servings(2)
            .moderationStatus(ModerationStatus.REJECTED)
            .rejectionReason(rejectionReason)
            .build();
    return recipeRepository.save(recipe);
  }

  private java.util.UUID ingredientIdOf(String normalizedName) {
    Ingredient ingredient = ingredientRepository.findByNameIgnoreCase(normalizedName).orElseThrow();
    return ingredient.getId();
  }
}
