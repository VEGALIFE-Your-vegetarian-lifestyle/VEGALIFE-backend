package com.vegalife.integration.controller.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.Difficulty;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.recipe.Unit;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
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
class AdminRecipeControllerIntegrationTest {

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

  @Autowired private RecipeRepository recipeRepository;

  @Autowired private DishRepository dishRepository;

  @Autowired private IngredientRepository ingredientRepository;

  @Autowired private EntityManager entityManager;

  @Autowired private JwtTokenService jwtTokenService;

  private User admin;
  private User member;
  private User author2;
  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    entityManager.createNativeQuery("DELETE FROM recipe_ingredient").executeUpdate();
    recipeRepository.deleteAll();
    postRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    admin = createUser("admincook", "admin.cook@example.com", UserRole.ADMIN);
    member = createUser("membercook", "member.cook@example.com", UserRole.USER);
    author2 = createUser("secondcook", "second.cook@example.com", UserRole.USER);
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(member);
  }

  @Test
  void listRecipes_asAdmin_returns200WithFullItemFieldsAndIncludesSoftDeleted() throws Exception {
    Category recipes = categoryRepository.saveAndFlush(Category.builder().name("Recipes").build());
    Post linkedPost =
        postRepository.save(
            Post.builder()
                .user(author2)
                .title("Bowl post")
                .content("Post content")
                .rawContent(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
                .categories(new HashSet<>(Set.of(recipes)))
                .status(PostStatus.published)
                .viewCount(3)
                .build());
    Recipe tofuBowl = createRecipe(author2, "Aardvark tofu bowl", null, Set.of(linkedPost), true);
    createRecipe(member, "Zebra soup", null, Set.of(), false);
    createRecipe(member, "Soft deleted", Instant.now(), Set.of(), false);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("sort", "name,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Recipes retrieved successfully"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(3))
        .andExpect(jsonPath("$.data.content[0].name").value("Aardvark tofu bowl"))
        .andExpect(jsonPath("$.data.content[0].id").value(tofuBowl.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].instructions").value("Stir and serve"))
        .andExpect(jsonPath("$.data.content[0].servings").value(2))
        .andExpect(jsonPath("$.data.content[0].difficulty").value("EASY"))
        .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.content[0].dishId").exists())
        .andExpect(jsonPath("$.data.content[0].dishName").value("Test bowl"))
        .andExpect(jsonPath("$.data.content[0].categoryIds[0]").value(recipes.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].ingredients[0].name").value("Tofu"))
        .andExpect(jsonPath("$.data.content[0].ingredients[0].amount").value(2.5))
        .andExpect(jsonPath("$.data.content[0].ingredients[0].unit").value("G"))
        // Per-line contribution: 2.5 x 1.44 = 3.60, matching the persisted total.
        .andExpect(jsonPath("$.data.content[0].ingredients[0].calories").value(3.6))
        .andExpect(jsonPath("$.data.content[0].ingredients[0].proteinG").value(0.43))
        .andExpect(jsonPath("$.data.content[0].ingredients[0].nutritionResolved").value(true))
        .andExpect(jsonPath("$.data.content[0].calories").value(3.6))
        .andExpect(jsonPath("$.data.content[0].proteinG").value(0.43))
        .andExpect(jsonPath("$.data.content[0].nutritionResolved").value(true))
        .andExpect(jsonPath("$.data.content[0].createdAt").exists())
        .andExpect(jsonPath("$.data.content[0].updatedAt").exists())
        .andExpect(jsonPath("$.data.content[0].userId").value(author2.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].displayName").value("secondcook"))
        .andExpect(jsonPath("$.data.content[0].email").value("second.cook@example.com"))
        .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.content[1].name").value("Soft deleted"))
        .andExpect(jsonPath("$.data.content[1].status").value("DELETED"));
  }

  @Test
  void getRecipe_reportsTheDishImage() throws Exception {
    Dish dish =
        dishRepository.saveAndFlush(
            Dish.builder()
                .name("Imaged bowl")
                .imageUrl("https://cdn.example.com/imaged-bowl.jpg")
                .build());
    Recipe recipe =
        recipeRepository.saveAndFlush(
            Recipe.builder()
                .dish(dish)
                .user(member)
                .name("Imaged recipe")
                .instructions("Stir and serve")
                .servings(2)
                .build());

    mockMvc
        .perform(
            get("/api/admin/recipes/{recipeId}", recipe.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/imaged-bowl.jpg"));
  }

  @Test
  void listRecipes_returnsAllAuthorsAndSoftDeletedRecipes() throws Exception {
    createRecipe(member, "Member active", null, Set.of(), false);
    createRecipe(author2, "Author active", null, Set.of(), false);
    createRecipe(admin, "Admin active", null, Set.of(), false);
    createRecipe(member, "Member removed", Instant.now(), Set.of(), false);

    mockMvc
        .perform(get("/api/admin/recipes").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(4))
        .andExpect(jsonPath("$.data.content.length()").value(4))
        .andExpect(
            jsonPath("$.data.content[*].status")
                .value(containsInAnyOrder("ACTIVE", "ACTIVE", "ACTIVE", "DELETED")))
        .andExpect(
            jsonPath("$.data.content[*].displayName")
                .value(containsInAnyOrder("membercook", "secondcook", "admincook", "membercook")));
  }

  @Test
  void listRecipes_filterByUserId_returnsOnlyThatAuthorsRecipes() throws Exception {
    createRecipe(member, "Member recipe", null, Set.of(), false);
    createRecipe(author2, "Author recipe", null, Set.of(), false);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("userId", author2.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Author recipe"))
        .andExpect(jsonPath("$.data.content[0].displayName").value("secondcook"))
        .andExpect(jsonPath("$.data.content[0].userId").value(author2.getId().toString()));
  }

  @Test
  void listRecipes_filterByCategoryId_returnsEachMatchingRecipeExactlyOnce() throws Exception {
    Category recipes = categoryRepository.saveAndFlush(Category.builder().name("Recipes").build());
    Category snacks = categoryRepository.saveAndFlush(Category.builder().name("Snacks").build());

    Post bothPosts =
        postRepository.save(
            Post.builder()
                .user(member)
                .title("Post in recipes")
                .content("Content")
                .rawContent(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
                .categories(new HashSet<>(Set.of(recipes)))
                .status(PostStatus.published)
                .viewCount(0)
                .build());
    Post secondPost =
        postRepository.save(
            Post.builder()
                .user(member)
                .title("Second post in recipes")
                .content("Content")
                .rawContent(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
                .categories(new HashSet<>(Set.of(recipes)))
                .status(PostStatus.published)
                .viewCount(0)
                .build());
    Post snackPost =
        postRepository.save(
            Post.builder()
                .user(member)
                .title("Post in snacks")
                .content("Content")
                .rawContent(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
                .categories(new HashSet<>(Set.of(snacks)))
                .status(PostStatus.published)
                .viewCount(0)
                .build());

    createRecipe(member, "In both linked posts", null, Set.of(bothPosts, secondPost), false);
    createRecipe(member, "In recipes only", null, Set.of(bothPosts), false);
    createRecipe(member, "In snacks only", null, Set.of(snackPost), false);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("categoryId", recipes.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(
            jsonPath("$.data.content[*].name")
                .value(containsInAnyOrder("In both linked posts", "In recipes only")));
  }

  @Test
  void listRecipes_filterByKeyword_returnsOnlyMatchingRecipes() throws Exception {
    createRecipe(author2, "Aardvark tofu bowl", null, Set.of(), false);
    createRecipe(member, "Zebra soup", null, Set.of(), false);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("keyword", "AARDVARK"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Aardvark tofu bowl"));
  }

  @Test
  void listRecipes_filterByKeywordMatchingIngredient_returnsTheRecipeOnce() throws Exception {
    createRecipe(author2, "Tofu bowl", null, Set.of(), true);
    createRecipe(member, "Zebra soup", null, Set.of(), false);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("keyword", "tofu"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Tofu bowl"));
  }

  @Test
  void listRecipes_filterByKeywordWithWildcard_matchesLiterally() throws Exception {
    createRecipe(author2, "Aardvark tofu bowl", null, Set.of(), false);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("keyword", "%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void listRecipes_filterByDeletionStatus_returnsOnlyThatState() throws Exception {
    createRecipe(author2, "Active recipe", null, Set.of(), false);
    createRecipe(member, "Removed recipe", Instant.now(), Set.of(), false);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "DELETED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Removed recipe"))
        .andExpect(jsonPath("$.data.content[0].status").value("DELETED"));
  }

  @Test
  void listRecipes_withoutDeletionStatus_returnsActiveAndDeletedRecipes() throws Exception {
    createRecipe(author2, "Active recipe", null, Set.of(), false);
    createRecipe(member, "Removed recipe", Instant.now(), Set.of(), false);

    mockMvc
        .perform(get("/api/admin/recipes").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void listRecipes_withInvalidDeletionStatus_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "ARCHIVED"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void listRecipes_filterByDifficulty_returnsOnlyThatDifficulty() throws Exception {
    createRecipe(author2, "Easy bowl", null, Set.of(), false);
    Recipe hard = createRecipe(member, "Hard bowl", null, Set.of(), false);
    hard.setDifficulty(Difficulty.HARD);
    recipeRepository.saveAndFlush(hard);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("difficulty", "hard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Hard bowl"));
  }

  @Test
  void listRecipes_withInvalidDifficulty_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("difficulty", "EXTREME"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void listRecipes_filterByMaxTotalTime_excludesRecipesOverTheLimit() throws Exception {
    // The helper creates prep 10 + cook 15 = 25 minutes.
    createRecipe(author2, "Quick bowl", null, Set.of(), false);
    Recipe slow = createRecipe(member, "Slow bowl", null, Set.of(), false);
    slow.setPrepTimeMinutes(30);
    slow.setCookTimeMinutes(60);
    recipeRepository.saveAndFlush(slow);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("maxTotalTimeMinutes", "30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Quick bowl"));
  }

  @Test
  void listRecipes_filterByDishId_returnsOnlyRecipesForThatDish() throws Exception {
    createRecipe(author2, "Test bowl recipe", null, Set.of(), false);
    Dish otherDish = dishRepository.saveAndFlush(Dish.builder().name("Other plate").build());
    recipeRepository.saveAndFlush(
        Recipe.builder()
            .dish(otherDish)
            .user(member)
            .name("Other plate recipe")
            .instructions("Stir and serve")
            .servings(2)
            .difficulty(Difficulty.EASY)
            .build());

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("dishId", otherDish.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Other plate recipe"));
  }

  @Test
  void listRecipes_filterByModerationStatus_returnsOnlyThatState() throws Exception {
    createRecipe(author2, "Pending recipe", null, Set.of(), false);
    Recipe approved = createRecipe(member, "Approved recipe", null, Set.of(), false);
    approved.setModerationStatus(ModerationStatus.APPROVED);
    recipeRepository.saveAndFlush(approved);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("moderationStatus", "approved"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Approved recipe"));
  }

  @Test
  void listRecipes_filterByCreatedDateRange_returnsOnlyMatchingRecipes() throws Exception {
    createRecipe(member, "First", null, Set.of(), false);
    createRecipe(author2, "Second", null, Set.of(), false);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2000-01-01T00:00:00Z")
                .param("createdTo", "2100-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2));

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2100-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.content.length()").value(0));
  }

  @Test
  void listRecipes_withoutJwt_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/recipes")).andExpect(status().isUnauthorized());
  }

  @Test
  void listRecipes_withNonAdminJwt_returns403() throws Exception {
    mockMvc
        .perform(get("/api/admin/recipes").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void listRecipes_withPagination_returnsSinglePageItem() throws Exception {
    createRecipe(member, "Aardvark recipe", null, Set.of(), false);
    createRecipe(member, "Mid recipe", null, Set.of(), false);
    createRecipe(member, "Zebra recipe", null, Set.of(), false);

    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "1")
                .param("sort", "name,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(3))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Aardvark recipe"));
  }

  @Test
  void listRecipes_withDisallowedSortProperty_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("sort", "passwordHash,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Sort property must be one of: createdAt, updatedAt, name, moderationStatus"));
  }

  @Test
  void listRecipes_sortingByScore_isRejected() throws Exception {
    // The score column and its sort property are gone (ADR-026); a client still sending it gets a
    // validation error rather than a silently different order.
    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("sort", "score,desc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("Sort property must be one of")));
  }

  @Test
  void listRecipes_withMalformedSort_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("sort", "createdAt,asc,extra"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Sort must be in the form property,asc|desc"));
  }

  @Test
  void listRecipes_withInvalidPaging_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("size", "101"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listRecipes_createdFromAfterCreatedTo_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2100-01-01T00:00:00Z")
                .param("createdTo", "2000-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("createdFrom must be before createdTo"));
  }

  @Test
  void listRecipes_withMalformedDate_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/recipes")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "not-a-date"))
        .andExpect(status().isBadRequest());
  }

  private User createUser(String displayName, String email, UserRole role) {
    return userRepository.save(
        User.builder()
            .displayName(displayName)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(role)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build());
  }

  private Recipe createRecipe(
      User owner, String name, Instant deletedAt, Set<Post> posts, boolean withIngredient) {
    // V22 enforces case-insensitive unique names on active dishes; reuse instead of insert.
    Dish dish =
        dishRepository
            .findByNameIgnoreCaseAndDeletedAtIsNull("Test bowl")
            .orElseGet(() -> dishRepository.save(Dish.builder().name("Test bowl").build()));
    Recipe recipe =
        recipeRepository.save(
            Recipe.builder()
                .dish(dish)
                .user(owner)
                .name(name)
                .description("Recipe description for " + name)
                .instructions("Stir and serve")
                .prepTimeMinutes(10)
                .cookTimeMinutes(15)
                .servings(2)
                .difficulty(Difficulty.EASY)
                .posts(new HashSet<>(posts))
                .deletedAt(deletedAt)
                .build());
    if (withIngredient) {
      Ingredient tofu =
          ingredientRepository
              .findByNameIgnoreCase("Tofu")
              .orElseGet(
                  () -> ingredientRepository.save(Ingredient.builder().name("Tofu").build()));
      // Curated so the response has a resolvable line: 2.5 x these values are the totals set below.
      tofu.setUnit(Unit.G);
      tofu.setCalories(new BigDecimal("1.440000"));
      tofu.setProteinG(new BigDecimal("0.171900"));
      tofu.setCarbohydrateG(new BigDecimal("0.027800"));
      tofu.setFatG(new BigDecimal("0.087200"));
      tofu.setFiberG(new BigDecimal("0.023000"));
      entityManager.flush();
      RecipeIngredient link =
          RecipeIngredient.builder()
              .recipeId(recipe.getId())
              .ingredientId(tofu.getId())
              .recipe(recipe)
              .ingredient(tofu)
              .amount(new BigDecimal("2.500"))
              .build();
      entityManager.persist(link);
      recipe.getRecipeIngredients().add(link);
      // The persisted totals ADR-022 would have computed; the read path must not recompute them.
      recipe.setCalories(new BigDecimal("3.60"));
      recipe.setProteinG(new BigDecimal("0.43"));
      recipe.setCarbohydrateG(new BigDecimal("0.07"));
      recipe.setFatG(new BigDecimal("0.22"));
      recipe.setFiberG(new BigDecimal("0.06"));
      entityManager.flush();
    }
    return recipe;
  }
}
