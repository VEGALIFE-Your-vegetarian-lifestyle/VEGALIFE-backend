package com.vegalife.integration.controller.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.user.User;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.math.BigDecimal;
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
class IngredientControllerIntegrationTest {

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

  @Autowired private IngredientRepository ingredientRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private String accessToken;

  @BeforeEach
  void setUp() {
    ingredientRepository.deleteAll();

    User testUser =
        User.builder()
            .username("ingredientuser")
            .email("ingredient@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    testUser = userRepository.save(testUser);
    accessToken = jwtTokenService.generateAccessToken(testUser);
  }

  @Test
  void listIngredients_withToken_returns200SortedByNameWithoutNutrition() throws Exception {
    saveIngredient("Tofu");
    saveIngredient("Tomato");
    saveIngredient("Avocado");

    mockMvc
        .perform(get("/api/ingredients").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Ingredients retrieved successfully"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.first").value(true))
        .andExpect(jsonPath("$.data.last").value(true))
        .andExpect(jsonPath("$.data.content.length()").value(3))
        .andExpect(jsonPath("$.data.content[0].name").value("Avocado"))
        .andExpect(jsonPath("$.data.content[1].name").value("Tofu"))
        .andExpect(jsonPath("$.data.content[2].name").value("Tomato"))
        .andExpect(jsonPath("$.data.content[1].id").isNotEmpty())
        .andExpect(jsonPath("$.data.content[1].createdAt").isNotEmpty())
        .andExpect(jsonPath("$.data.content[0].calories").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].proteinG").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].protein_g").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].carbohydrateG").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].fatG").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].fiberG").doesNotExist());
  }

  @Test
  void listIngredients_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/ingredients")).andExpect(status().isUnauthorized());
  }

  @Test
  void listIngredients_nameFilter_matchesCaseInsensitively() throws Exception {
    saveIngredient("Sweet Tofu");
    saveIngredient("tofu");
    saveIngredient("Broccoli");

    mockMvc
        .perform(
            get("/api/ingredients")
                .header("Authorization", "Bearer " + accessToken)
                .param("name", "TOF"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[0].name").value("Sweet Tofu"))
        .andExpect(jsonPath("$.data.content[1].name").value("tofu"));
  }

  @Test
  void listIngredients_paging_returnsDisjointStablePages() throws Exception {
    saveIngredient("Beans");
    saveIngredient("Carrot");
    saveIngredient("Daikon");

    mockMvc
        .perform(
            get("/api/ingredients")
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "0")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.first").value(true))
        .andExpect(jsonPath("$.data.last").value(false))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.content[0].name").value("Beans"))
        .andExpect(jsonPath("$.data.content[1].name").value("Carrot"));

    mockMvc
        .perform(
            get("/api/ingredients")
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "1")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.first").value(false))
        .andExpect(jsonPath("$.data.last").value(true))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Daikon"));
  }

  @Test
  void listIngredients_emptyTable_returns200WithEmptyContent() throws Exception {
    mockMvc
        .perform(get("/api/ingredients").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content.length()").value(0))
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.totalPages").value(0));
  }

  @Test
  void listIngredients_invalidSize_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/ingredients")
                .header("Authorization", "Bearer " + accessToken)
                .param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void listIngredients_negativePage_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/ingredients")
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listIngredients_isReadOnly_doesNotCreateUpdateOrDeleteRows() throws Exception {
    saveIngredient("Tofu");
    long before = ingredientRepository.count();

    mockMvc
        .perform(
            get("/api/ingredients")
                .header("Authorization", "Bearer " + accessToken)
                .param("name", "tof"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));

    assertThat(ingredientRepository.count()).isEqualTo(before);
    List<Ingredient> all = ingredientRepository.findAll();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).getName()).isEqualTo("Tofu");
  }

  private void saveIngredient(String name) {
    Ingredient ingredient =
        Ingredient.builder()
            .name(name)
            .calories(new BigDecimal("100.00"))
            .proteinG(new BigDecimal("10.00"))
            .carbohydrateG(new BigDecimal("5.00"))
            .fatG(new BigDecimal("2.00"))
            .fiberG(new BigDecimal("3.00"))
            .build();
    ingredientRepository.save(ingredient);
  }
}
