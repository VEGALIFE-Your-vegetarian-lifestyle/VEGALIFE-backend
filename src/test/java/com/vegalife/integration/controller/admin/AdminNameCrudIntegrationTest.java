package com.vegalife.integration.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
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
 * Admin CRUD over dish and ingredient names, end to end against Postgres (ADR-016).
 *
 * <p>The interesting cases are not the 201s but the visibility rules: a name created by an admin is
 * approved on arrival, a rejected name cannot be re-created by a member, and deleting a name hides
 * it without freeing it.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class AdminNameCrudIntegrationTest {

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

  @Autowired private ModerationLogRepository moderationLogRepository;

  @Autowired private RecipeRepository recipeRepository;

  @Autowired private RecipeIngredientRepository recipeIngredientRepository;

  @Autowired private DishRepository dishRepository;

  @Autowired private IngredientRepository ingredientRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private String adminToken;
  private String memberToken;

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
            createUser("adminnames", "admin.names@example.com", UserRole.ADMIN));
    memberToken =
        jwtTokenService.generateAccessToken(
            createUser("membernames", "member.names@example.com", UserRole.USER));
  }

  @Test
  void createDish_isApprovedAndVisibleInThePublicListing() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/dishes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  bún chả chay \",\"cuisineType\":\"Vietnamese\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value("bún chả chay"))
        .andExpect(jsonPath("$.data.moderationStatus").value("APPROVED"));

    // The creator is the approver, so it is usable immediately.
    mockMvc
        .perform(
            get("/api/dishes")
                .header("Authorization", "Bearer " + memberToken)
                .param("name", "bún chả"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].name").value("bún chả chay"));
  }

  @Test
  void createDish_duplicateName_returns409() throws Exception {
    dishRepository.saveAndFlush(
        Dish.builder().name("phở chay").moderationStatus(ModerationStatus.APPROVED).build());

    mockMvc
        .perform(
            post("/api/admin/dishes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"PHỞ CHAY\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void createDish_rejectedName_returns400AndIsNotRecreated() throws Exception {
    Dish rejected =
        dishRepository.saveAndFlush(
            Dish.builder().name("thịt chó").moderationStatus(ModerationStatus.REJECTED).build());

    mockMvc
        .perform(
            post("/api/admin/dishes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"thịt chó\"}"))
        .andExpect(status().isBadRequest());

    // The block must survive the attempt: still one row, still rejected.
    assertThat(dishRepository.findByNameIgnoreCase("thịt chó").orElseThrow().getId())
        .isEqualTo(rejected.getId());
    assertThat(dishRepository.findByNameIgnoreCase("thịt chó").orElseThrow().getModerationStatus())
        .isEqualTo(ModerationStatus.REJECTED);
  }

  @Test
  void updateDish_keepsApprovalAndAppliesFields() throws Exception {
    Dish dish =
        dishRepository.saveAndFlush(
            Dish.builder().name("phở chay").moderationStatus(ModerationStatus.APPROVED).build());

    mockMvc
        .perform(
            patch("/api/admin/dishes/" + dish.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Noodle soup\",\"cuisineType\":\"Vietnamese\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.description").value("Noodle soup"))
        // An edit is itself an approval; it must not re-queue the name.
        .andExpect(jsonPath("$.data.moderationStatus").value("APPROVED"));
  }

  @Test
  void deleteDish_hidesItButKeepsTheRowAndTheName() throws Exception {
    Dish dish =
        dishRepository.saveAndFlush(
            Dish.builder().name("phở chay").moderationStatus(ModerationStatus.APPROVED).build());

    mockMvc
        .perform(
            delete("/api/admin/dishes/" + dish.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/dishes/" + dish.getId()).header("Authorization", "Bearer " + memberToken))
        .andExpect(status().isNotFound());

    // Never hard-deleted: existing recipes and menu rows still point at it.
    assertThat(dishRepository.findById(dish.getId()).orElseThrow().getDeletedAt()).isNotNull();
    // And the name is not freed for a member to re-create.
    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(recipeBody("phở chay")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Dish is not available: phở chay"));
  }

  @Test
  void approveDish_makesAPendingNameVisibleAndRejectHidesItAgain() throws Exception {
    Dish dish =
        dishRepository.saveAndFlush(
            Dish.builder()
                .name("bánh xèo chay")
                .moderationStatus(ModerationStatus.PENDING)
                .build());

    mockMvc
        .perform(
            get("/api/dishes")
                .header("Authorization", "Bearer " + memberToken)
                .param("name", "bánh xèo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty());

    mockMvc
        .perform(
            post("/api/admin/dishes/" + dish.getId() + "/approve")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"real dish\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.moderationStatus").value("APPROVED"));

    mockMvc
        .perform(
            get("/api/dishes")
                .header("Authorization", "Bearer " + memberToken)
                .param("name", "bánh xèo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].name").value("bánh xèo chay"));

    mockMvc
        .perform(
            post("/api/admin/dishes/" + dish.getId() + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"not vegan\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.moderationStatus").value("REJECTED"));
  }

  @Test
  void rejectDish_blocksAMemberFromReusingTheName() throws Exception {
    Dish dish =
        dishRepository.saveAndFlush(
            Dish.builder().name("món lạ").moderationStatus(ModerationStatus.APPROVED).build());

    mockMvc
        .perform(
            post("/api/admin/dishes/" + dish.getId() + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(recipeBody("món lạ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Dish is not available: món lạ"));
  }

  @Test
  void createIngredient_isApprovedAndRejectionBlocksReuse() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/ingredients")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"đậu hũ\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.moderationStatus").value("APPROVED"));

    mockMvc
        .perform(
            get("/api/ingredients")
                .header("Authorization", "Bearer " + memberToken)
                .param("name", "đậu hũ"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].name").value("đậu hũ"));

    Ingredient saved = ingredientRepository.findByNameIgnoreCase("đậu hũ").orElseThrow();
    mockMvc
        .perform(
            post("/api/admin/ingredients/" + saved.getId() + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/recipes")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Tofu bowl",
                      "dishName": "bowl",
                      "instructions": "Chop and cook",
                      "servings": 2,
                      "ingredients": [{"name": "đậu hũ", "amount": 150, "unit": "g"}]
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Ingredient is not available: đậu hũ"));
  }

  @Test
  void listDishes_asAdmin_canFilterToTheReviewQueue() throws Exception {
    dishRepository.saveAndFlush(
        Dish.builder().name("đang chờ").moderationStatus(ModerationStatus.PENDING).build());
    dishRepository.saveAndFlush(
        Dish.builder().name("đã duyệt").moderationStatus(ModerationStatus.APPROVED).build());

    mockMvc
        .perform(
            get("/api/admin/dishes")
                .header("Authorization", "Bearer " + adminToken)
                .param("moderationStatus", "PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("đang chờ"));
  }

  @Test
  void listDishes_withUnknownModerationStatus_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/dishes")
                .header("Authorization", "Bearer " + adminToken)
                .param("moderationStatus", "MAYBE"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createDish_withMemberJwt_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/dishes")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"anything\"}"))
        .andExpect(status().isForbidden());
  }

  private String recipeBody(String dishName) {
    return """
        {
          "name": "Test recipe",
          "dishName": "%s",
          "instructions": "Chop and cook",
          "servings": 2,
          "ingredients": [{"name": "water", "amount": 100, "unit": "ml"}]
        }
        """
        .formatted(dishName);
  }

  private User createUser(String username, String email, UserRole role) {
    return userRepository.save(
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(role)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build());
  }
}
