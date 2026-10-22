package com.vegalife.integration.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.ModerationStatus;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.User;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Migration and visibility tests for component-level moderation (issue #160, ADR-016) against a
 * real Postgres.
 *
 * <p>The backfill assertion is the one that matters most: {@code DEFAULT 'PENDING'} on a table
 * whose rows are referenced by {@code menu_detail.dish_id NOT NULL} would, without the same
 * migration's {@code UPDATE ... 'APPROVED'}, hide every existing dish and orphan every saved menu.
 * A unit test cannot catch that — it needs the real DDL.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ModerationStatusIntegrationTest {

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

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private DishRepository dishRepository;

  @Autowired private IngredientRepository ingredientRepository;

  @Autowired private RecipeRepository recipeRepository;

  @Autowired private UserRepository userRepository;

  private User author;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM recipe_ingredient");
    jdbcTemplate.update("DELETE FROM post_recipe");
    jdbcTemplate.update("DELETE FROM recipe");
    jdbcTemplate.update("DELETE FROM menu_detail");
    jdbcTemplate.update("DELETE FROM menu");
    jdbcTemplate.update("DELETE FROM dish");
    jdbcTemplate.update("DELETE FROM ingredient");
    jdbcTemplate.update("DELETE FROM \"user\"");

    author =
        userRepository.saveAndFlush(
            User.builder()
                .username("moderator")
                .email("moderator@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
  }

  @Test
  void schemaDefaultsNewNamesToPendingButExistingRowsWereBackfilled() {
    // The CHECK constraints exist, so an invalid status is rejected at the database, not just in
    // Java.
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname IN"
                    + " ('chk_dish_moderation_status', 'chk_ingredient_moderation_status',"
                    + " 'chk_recipe_moderation_status')",
                Integer.class))
        .isEqualTo(3);

    // A row inserted without an explicit status lands PENDING: that is what a member's recipe
    // introduction produces.
    UUID dishId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO dish (id, name, created_at, updated_at) VALUES (?, ?, now(), now())",
        dishId,
        "món thử nghiệm");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT moderation_status FROM dish WHERE id = ?", String.class, dishId))
        .isEqualTo("PENDING");
  }

  @Test
  void publicListingsHidePendingAndRejectedNames() {
    Dish approved = saveDish("phở chay", ModerationStatus.APPROVED);
    saveDish("món chưa duyệt", ModerationStatus.PENDING);
    saveDish("món bị từ chối", ModerationStatus.REJECTED);
    Ingredient approvedIngredient = saveIngredient("tofu", ModerationStatus.APPROVED);
    saveIngredient("bột lạ", ModerationStatus.PENDING);

    List<Dish> visibleDishes =
        dishRepository.findByDeletedAtIsNullAndModerationStatusOrderByNameAsc(
            ModerationStatus.APPROVED);
    List<Ingredient> visibleIngredients =
        ingredientRepository.findByDeletedAtIsNullAndModerationStatusOrderByNameAsc(
            ModerationStatus.APPROVED);

    assertThat(visibleDishes).extracting(Dish::getId).containsExactly(approved.getId());
    assertThat(visibleIngredients)
        .extracting(Ingredient::getId)
        .containsExactly(approvedIngredient.getId());
  }

  @Test
  @Transactional
  void anExistingRecipeAndItsDishStillResolve() {
    Dish dish = saveDish("bún chả chay", ModerationStatus.APPROVED);
    Recipe recipe =
        recipeRepository.saveAndFlush(
            Recipe.builder()
                .dish(dish)
                .user(author)
                .name("bún chả chay")
                .instructions("Nướng chả, pha nước mắm chay")
                .servings(2)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());
    UUID recipeId = recipe.getId();

    // A recipe written before moderation existed must still resolve its dish on deploy, and the
    // dish must not have become invisible. The read is inside a transaction so the lazy
    // association is loaded the way application code loads it.
    Recipe reloaded = recipeRepository.findById(recipeId).orElseThrow();

    assertThat(reloaded.getDish().getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
    assertThat(reloaded.getDish().getName()).isEqualTo("bún chả chay");
  }

  @Test
  void aPendingRecipeIsNotScoredAndKeepsANullScore() {
    Dish dish = saveDish("bánh xèo", ModerationStatus.APPROVED);
    Recipe recipe =
        recipeRepository.saveAndFlush(
            Recipe.builder()
                .dish(dish)
                .user(author)
                .name("bánh xèo")
                .instructions("Tráng bột, rải giá đỗ")
                .servings(2)
                .build());

    Recipe reloaded = recipeRepository.findById(recipe.getId()).orElseThrow();

    assertThat(reloaded.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
    // NULL means never scored, which is distinct from "scored badly" (ADR-016).
    assertThat(reloaded.getScore()).isNull();
  }

  private Dish saveDish(String name, ModerationStatus status) {
    return dishRepository.saveAndFlush(Dish.builder().name(name).moderationStatus(status).build());
  }

  private Ingredient saveIngredient(String name, ModerationStatus status) {
    return ingredientRepository.saveAndFlush(
        Ingredient.builder().name(name).moderationStatus(status).build());
  }
}
