package com.vegalife.integration.controller.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.List;
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

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private DishRepository dishRepository;

  @Autowired private IngredientRepository ingredientRepository;

  @Autowired private RecipeRepository recipeRepository;

  @Autowired private RecipeIngredientRepository recipeIngredientRepository;

  private String accessToken;

  @BeforeEach
  void setUp() {
    User testUser =
        User.builder()
            .username("recipeuser")
            .email("recipe@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    testUser = userRepository.save(testUser);
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
            {"name": "Soy Sauce", "amount": 1.5, "unit": "tbsp"}
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
        .andExpect(jsonPath("$.data.ingredients[1].name").value("soy sauce"));

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
    assertThat(tofuLink.getUnit()).isEqualTo("g");
  }

  @Test
  void createRecipe_repeatedMixedCaseRequests_reuseSameDishAndIngredient() throws Exception {
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
    assertThat(ingredientRepository.count()).isEqualTo(1);
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

  private java.util.UUID ingredientIdOf(String normalizedName) {
    Ingredient ingredient = ingredientRepository.findByNameIgnoreCase(normalizedName).orElseThrow();
    return ingredient.getId();
  }
}
