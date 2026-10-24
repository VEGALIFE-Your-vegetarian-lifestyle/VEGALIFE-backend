package com.vegalife.integration.controller.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.ModerationStatus;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.model.user.DietaryPreferences;
import com.vegalife.model.user.User;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
class DishControllerIntegrationTest {

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

  @Autowired private UserProfileRepository userProfileRepository;

  private String accessToken;

  @BeforeEach
  void setUp() {
    userProfileRepository.deleteAll();
    recipeIngredientRepository.deleteAll();
    ingredientRepository.deleteAll();
    recipeRepository.deleteAll();
    dishRepository.deleteAll();
    User testUser =
        User.builder()
            .username("dishuser")
            .email("dish@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    testUser = userRepository.save(testUser);
    accessToken = jwtTokenService.generateAccessToken(testUser);
  }

  @Test
  void listDishes_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/dishes")).andExpect(status().isUnauthorized());
  }

  @Test
  void listDishes_withToken_returns200DefaultsExcludingSoftDeletedSortedByName() throws Exception {
    dishRepository.save(approved("tofu stir-fry"));
    dishRepository.save(approved("banh mi chay"));
    dishRepository.save(approved("old dish", Instant.now()));

    mockMvc
        .perform(get("/api/dishes").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Dishes retrieved successfully"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.first").value(true))
        .andExpect(jsonPath("$.data.last").value(true))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.content[0].name").value("banh mi chay"))
        .andExpect(jsonPath("$.data.content[1].name").value("tofu stir-fry"))
        .andExpect(jsonPath("$.data.content[0].id").isNotEmpty());

    assertThat(dishRepository.count()).isEqualTo(3);
  }

  @Test
  void listDishes_nameFilter_returnsOnlyMatchingDishesCaseInsensitively() throws Exception {
    dishRepository.save(approved("pho chay"));
    dishRepository.save(approved("tofu stir-fry"));

    mockMvc
        .perform(
            get("/api/dishes")
                .header("Authorization", "Bearer " + accessToken)
                .param("name", "PHO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("pho chay"));
  }

  @Test
  void listDishes_pageAndSize_returnsRequestedPage() throws Exception {
    dishRepository.save(approved("aaa"));
    dishRepository.save(approved("bbb"));
    dishRepository.save(approved("ccc"));

    mockMvc
        .perform(
            get("/api/dishes")
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "1")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.first").value(false))
        .andExpect(jsonPath("$.data.last").value(true))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("ccc"));
  }

  @Test
  void listDishes_negativePage_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/dishes").header("Authorization", "Bearer " + accessToken).param("page", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listDishes_sizeBelowMinimum_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/dishes").header("Authorization", "Bearer " + accessToken).param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listDishes_sizeAboveMaximum_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/dishes")
                .header("Authorization", "Bearer " + accessToken)
                .param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listDishes_emptyTable_returns200WithEmptyContent() throws Exception {
    mockMvc
        .perform(get("/api/dishes").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content.length()").value(0))
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.totalPages").value(0));
  }

  @Test
  void listDishes_returnsPersistedIdForRecipeLookupRoundTrip() throws Exception {
    Dish saved = dishRepository.save(approved("pho chay", "Noodle soup"));

    mockMvc
        .perform(get("/api/dishes").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].id").value(saved.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].name").value("pho chay"))
        .andExpect(jsonPath("$.data.content[0].description").value("Noodle soup"));
  }

  @Test
  void listDishes_performsNoWrites() throws Exception {
    dishRepository.save(approved("pho chay"));
    dishRepository.save(approved("tofu stir-fry"));
    long before = dishRepository.count();

    mockMvc
        .perform(get("/api/dishes").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());

    List<Dish> dishes = dishRepository.findAll();
    assertThat(dishes).hasSize((int) before);
    assertThat(dishes).allMatch(dish -> dish.getDeletedAt() == null);
  }

  @Test
  void listDishes_hidesNamesAwaitingAdminApproval() throws Exception {
    dishRepository.save(approved("pho chay"));
    dishRepository.save(
        Dish.builder().name("mon chua duyet").moderationStatus(ModerationStatus.PENDING).build());
    dishRepository.save(
        Dish.builder().name("mon bi tu choi").moderationStatus(ModerationStatus.REJECTED).build());

    mockMvc
        .perform(get("/api/dishes").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("pho chay"));
  }

  @Test
  void suggestDishes_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/dishes/suggestions")).andExpect(status().isUnauthorized());
  }

  @Test
  void suggestDishes_excludesDishesWhoseOnlyRecipeUsesAStoredAllergen() throws Exception {
    Dish safeDish = dishRepository.save(approved("pho chay"));
    Dish unsafeDish = dishRepository.save(approved("pad thai"));
    recipeRepository.save(approvedRecipe(safeDish, "Pho chay", "tofu"));
    recipeRepository.save(approvedRecipe(unsafeDish, "Pad thai", "peanut"));
    saveAllergies("Peanut");

    mockMvc
        .perform(get("/api/dishes/suggestions").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("pho chay"))
        .andExpect(jsonPath("$.data.content[0].recommendedRecipes[0].name").value("Pho chay"))
        .andExpect(jsonPath("$.data.content[0].recommendedRecipes[0].servings").value(2))
        .andExpect(jsonPath("$.data.content[0].recommendedRecipes[0].proteinG").value(21.50));
  }

  @Test
  void suggestDishes_hidesDishesWithNoApprovedRecipe() throws Exception {
    dishRepository.save(approved("pho chay"));

    mockMvc
        .perform(get("/api/dishes/suggestions").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.content.length()").value(0));
  }

  @Test
  void suggestDishes_reportsNutritionAsNullRatherThanZeroWhenUnknown() throws Exception {
    Dish dish = dishRepository.save(approved("pho chay"));
    recipeRepository.save(approvedRecipe(dish, "Pho chay", "tofu"));

    mockMvc
        .perform(get("/api/dishes/suggestions").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].recommendedRecipes[0].calories").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].recommendedRecipes[0].fatG").doesNotExist());
  }

  @Test
  void suggestDishes_calorieCeilingExcludesRecipesWithUnknownCalories() throws Exception {
    Dish dish = dishRepository.save(approved("pho chay"));
    recipeRepository.save(approvedRecipe(dish, "Pho chay", "tofu"));

    mockMvc
        .perform(
            get("/api/dishes/suggestions")
                .header("Authorization", "Bearer " + accessToken)
                .param("maxCalories", "800"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void suggestDishes_negativeCalories_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/dishes/suggestions")
                .header("Authorization", "Bearer " + accessToken)
                .param("maxCalories", "-1"))
        .andExpect(status().isBadRequest());
  }

  /**
   * Catalogue fixtures model data that existed before moderation was introduced, so they are
   * approved. A PENDING dish is one a member introduced and no admin has reviewed yet (ADR-016).
   */
  private static Dish approved(String name) {
    return Dish.builder().name(name).moderationStatus(ModerationStatus.APPROVED).build();
  }

  private Recipe approvedRecipe(Dish dish, String name, String ingredientName) {
    User owner = userRepository.findAll().get(0);
    Ingredient ingredient =
        ingredientRepository.saveAndFlush(Ingredient.builder().name(ingredientName).build());
    Recipe recipe =
        recipeRepository.saveAndFlush(
            Recipe.builder()
                .dish(dish)
                .user(owner)
                .name(name)
                .instructions("Cook it")
                .servings(2)
                .proteinG(new BigDecimal("21.50"))
                .moderationStatus(ModerationStatus.APPROVED)
                .build());
    recipeIngredientRepository.saveAndFlush(
        RecipeIngredient.builder()
            .recipeId(recipe.getId())
            .ingredientId(ingredient.getId())
            .amount(BigDecimal.ONE)
            .unit("g")
            .build());
    return recipe;
  }

  private void saveAllergies(String... allergies) {
    UserProfile profile =
        UserProfile.builder()
            .user(userRepository.findAll().get(0))
            .dietaryPreferences(DietaryPreferences.builder().allergies(List.of(allergies)).build())
            .build();
    // flush: the request runs in the same transaction, so an unflushed profile would be invisible
    // to the service's own query and the allergy filter would silently no-op.
    userProfileRepository.saveAndFlush(profile);
  }

  private static Dish approved(String name, String description) {
    return Dish.builder()
        .name(name)
        .description(description)
        .moderationStatus(ModerationStatus.APPROVED)
        .build();
  }

  private static Dish approved(String name, Instant deletedAt) {
    return Dish.builder()
        .name(name)
        .deletedAt(deletedAt)
        .moderationStatus(ModerationStatus.APPROVED)
        .build();
  }
}
