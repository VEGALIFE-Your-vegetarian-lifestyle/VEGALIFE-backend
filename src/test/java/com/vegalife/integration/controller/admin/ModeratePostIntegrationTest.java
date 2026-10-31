package com.vegalife.integration.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Approve/reject for posts, end to end against Postgres (ADR-016).
 *
 * <p>These replace the {@code /moderate} tests. The important ones are not the status flips — those
 * are easy — but the component rules: approving approves the names the post introduced, rejecting
 * refuses only the ones still pending, and neither ever undoes a decision someone already made.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class ModeratePostIntegrationTest {

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

  private User admin;
  private User member;
  private String adminToken;
  private String userToken;
  private Category category;

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

    admin = createUser("adminmod", "admin.mod@example.com", User.Role.ADMIN);
    member = createUser("membermod", "member.mod@example.com", User.Role.USER);
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(member);
    category = categoryRepository.saveAndFlush(Category.builder().name("Dinner").build());
  }

  @Test
  void approve_publishesAndLogsReason_leavingFlagUntouched() throws Exception {
    Post post =
        createPost(
            member, Post.Status.unpublished, Post.Flag.REJECTED, new HashSet<>(Set.of(category)));

    approve(post, "{\"reason\":\"Vegan recipe false positive\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Post approved successfully"))
        .andExpect(jsonPath("$.data.status").value("published"))
        .andExpect(jsonPath("$.data.flag").value("REJECTED"));

    Post reloaded = postRepository.findById(post.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(Post.Status.published);
    assertThat(reloaded.getPublishedAt()).isNotNull();
    // Auto-REJECTED records what the filter decided; approving does not rewrite that history.
    assertThat(reloaded.getFlag()).isEqualTo(Post.Flag.REJECTED);

    ModerationLog entry = singlePostLog();
    assertThat(entry.getActorId()).isEqualTo(admin.getId());
    assertThat(entry.getAction()).isEqualTo("APPROVE_POST");
    assertThat(entry.getReason()).isEqualTo("Vegan recipe false positive");
  }

  @Test
  void approve_approvesEveryPendingComponentOfThePost() throws Exception {
    Post post =
        createPost(
            member,
            Post.Status.unpublished,
            Post.Flag.NEEDS_REVIEW,
            new HashSet<>(Set.of(category)));
    Dish dish = saveDish("bún chả chay", ModerationStatus.PENDING);
    Ingredient ingredient = saveIngredient("đậu hũ", ModerationStatus.PENDING);
    Recipe recipe = saveRecipe(post, dish, ModerationStatus.PENDING);
    linkIngredient(recipe, ingredient);

    approve(post, "{}").andExpect(status().isOk());

    assertThat(recipeRepository.findById(recipe.getId()).orElseThrow().getModerationStatus())
        .isEqualTo(ModerationStatus.APPROVED);
    assertThat(dishRepository.findById(dish.getId()).orElseThrow().getModerationStatus())
        .isEqualTo(ModerationStatus.APPROVED);
    assertThat(
            ingredientRepository.findById(ingredient.getId()).orElseThrow().getModerationStatus())
        .isEqualTo(ModerationStatus.APPROVED);
  }

  @Test
  void approve_doesNotResurrectARejectedName() throws Exception {
    Post post =
        createPost(
            member,
            Post.Status.unpublished,
            Post.Flag.NEEDS_REVIEW,
            new HashSet<>(Set.of(category)));
    Dish rejected = saveDish("món bị từ chối", ModerationStatus.REJECTED);
    Recipe recipe = saveRecipe(post, rejected, ModerationStatus.PENDING);

    approve(post, "{}").andExpect(status().isOk());

    // A rejected name is a permanent block (BR-DISH-001). Approving a post that mentions it must
    // not quietly undo a decision another admin already made.
    assertThat(dishRepository.findById(rejected.getId()).orElseThrow().getModerationStatus())
        .isEqualTo(ModerationStatus.REJECTED);
  }

  @Test
  void reject_unpublishesAndRejectsOnlyPendingComponents() throws Exception {
    Post post =
        createPost(
            member, Post.Status.published, Post.Flag.PASSED, new HashSet<>(Set.of(category)));
    Dish pendingDish = saveDish("món lạ", ModerationStatus.PENDING);
    Ingredient pendingIngredient = saveIngredient("nguyên liệu lạ", ModerationStatus.PENDING);
    Recipe pendingRecipe = saveRecipe(post, pendingDish, ModerationStatus.PENDING);
    linkIngredient(pendingRecipe, pendingIngredient);

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"not vegan\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Post rejected successfully"))
        .andExpect(jsonPath("$.data.status").value("unpublished"));

    Post reloaded = postRepository.findById(post.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(Post.Status.unpublished);
    assertThat(reloaded.getPublishedAt()).isNull();
    assertThat(pendingDish.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
    assertThat(pendingIngredient.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
    // Rejection never deletes: existing references must keep resolving.
    assertThat(dishRepository.findById(pendingDish.getId())).isPresent();
    assertThat(singlePostLog().getAction()).isEqualTo("REJECT_POST");
  }

  @Test
  void reject_leavesAnAlreadyApprovedNameApproved() throws Exception {
    Post post =
        createPost(
            member, Post.Status.published, Post.Flag.PASSED, new HashSet<>(Set.of(category)));
    Dish approvedDish = saveDish("phở chay", ModerationStatus.APPROVED);
    saveRecipe(post, approvedDish, ModerationStatus.APPROVED);

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    // One bad post must not knock out a name other members' recipes and menus depend on.
    assertThat(dishRepository.findById(approvedDish.getId()).orElseThrow().getModerationStatus())
        .isEqualTo(ModerationStatus.APPROVED);
  }

  @Test
  void getPostDetail_returnsScoresAndComponentStates() throws Exception {
    Post post =
        createPost(
            member,
            Post.Status.unpublished,
            Post.Flag.NEEDS_REVIEW,
            new HashSet<>(Set.of(category)));
    post.setContentScore(0.64);
    postRepository.saveAndFlush(post);
    Dish dish = saveDish("bún chả chay", ModerationStatus.PENDING);
    Ingredient ingredient = saveIngredient("đậu hũ", ModerationStatus.APPROVED);
    Recipe recipe = saveRecipe(post, dish, ModerationStatus.PENDING);
    recipe.setScore(0.79);
    recipeRepository.saveAndFlush(recipe);
    linkIngredient(recipe, ingredient);

    mockMvc
        .perform(
            get("/api/admin/posts/" + post.getId()).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.contentScore").value(0.64))
        .andExpect(jsonPath("$.data.recipes[0].score").value(0.79))
        .andExpect(jsonPath("$.data.recipes[0].type").value("RECIPE"))
        .andExpect(jsonPath("$.data.names.length()").value(2))
        .andExpect(jsonPath("$.data.names[0].moderationStatus").value("PENDING"))
        .andExpect(jsonPath("$.data.names[1].moderationStatus").value("APPROVED"));
  }

  @Test
  void getPostDetail_withoutJwt_returns401() throws Exception {
    Post post =
        createPost(
            member, Post.Status.published, Post.Flag.PASSED, new HashSet<>(Set.of(category)));

    mockMvc.perform(get("/api/admin/posts/" + post.getId())).andExpect(status().isUnauthorized());
  }

  @Test
  void getPostDetail_withNonAdminJwt_returns403() throws Exception {
    Post post =
        createPost(
            member, Post.Status.published, Post.Flag.PASSED, new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            get("/api/admin/posts/" + post.getId()).header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void approve_withoutCategory_returns400AndNoLog() throws Exception {
    Post post = createPost(member, Post.Status.unpublished, Post.Flag.REJECTED, new HashSet<>());

    approve(post, "{\"reason\":\"trying\"}")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message").value("At least one category is required to publish a post"));

    assertThat(moderationLogRepository.findAll()).isEmpty();
  }

  @Test
  void approve_hiddenPost_returns400() throws Exception {
    Post post =
        createPost(member, Post.Status.hidden, Post.Flag.REJECTED, new HashSet<>(Set.of(category)));

    approve(post, "{}").andExpect(status().isBadRequest());
  }

  @Test
  void reject_hiddenPost_returns400() throws Exception {
    Post post =
        createPost(member, Post.Status.hidden, Post.Flag.REJECTED, new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void approve_missingPost_returns404() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/posts/00000000-0000-0000-0000-000000000000/approve")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void approve_withoutBody_succeedsBecauseReasonIsOptional() throws Exception {
    Post post =
        createPost(
            member,
            Post.Status.unpublished,
            Post.Flag.NEEDS_REVIEW,
            new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/approve")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("published"));
  }

  @Test
  void approve_withReasonTooLong_returns400() throws Exception {
    Post post =
        createPost(
            member,
            Post.Status.unpublished,
            Post.Flag.NEEDS_REVIEW,
            new HashSet<>(Set.of(category)));

    approve(post, "{\"reason\":\"" + "x".repeat(1001) + "\"}").andExpect(status().isBadRequest());
  }

  @Test
  void moderateEndpointNoLongerExists() throws Exception {
    Post post =
        createPost(
            member,
            Post.Status.unpublished,
            Post.Flag.NEEDS_REVIEW,
            new HashSet<>(Set.of(category)));

    // Removed outright rather than deprecated: leaving it would let a client keep publishing
    // without ever deciding on the components (ADR-016).
    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/moderate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PUBLISH\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void approve_withNonAdminJwt_returns403() throws Exception {
    Post post =
        createPost(
            member, Post.Status.unpublished, Post.Flag.REJECTED, new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/approve")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    assertThat(moderationLogRepository.findAll()).isEmpty();
  }

  @Test
  void approve_withoutJwt_returns401() throws Exception {
    Post post =
        createPost(
            member, Post.Status.unpublished, Post.Flag.REJECTED, new HashSet<>(Set.of(category)));

    mockMvc
        .perform(
            post("/api/admin/posts/" + post.getId() + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  private ResultActions approve(Post post, String body) throws Exception {
    return mockMvc.perform(
        post("/api/admin/posts/" + post.getId() + "/approve")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  /** The post-level log entry, ignoring the per-component ones written alongside it. */
  private ModerationLog singlePostLog() {
    List<ModerationLog> postLogs =
        moderationLogRepository.findAll().stream()
            .filter(entry -> "POST".equals(entry.getTargetType()))
            .toList();
    assertThat(postLogs).hasSize(1);
    return postLogs.get(0);
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

  private Post createPost(
      User owner, Post.Status status, Post.Flag flag, Set<Category> categories) {
    return postRepository.saveAndFlush(
        Post.builder()
            .user(owner)
            .title("Moderated post")
            .content("Post content")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .categories(categories)
            .status(status)
            .flag(flag)
            .publishedAt(status == Post.Status.published ? Instant.now() : null)
            .viewCount(0)
            .build());
  }

  private Dish saveDish(String name, ModerationStatus status) {
    return dishRepository.saveAndFlush(Dish.builder().name(name).moderationStatus(status).build());
  }

  private Ingredient saveIngredient(String name, ModerationStatus status) {
    return ingredientRepository.saveAndFlush(
        Ingredient.builder().name(name).moderationStatus(status).build());
  }

  private Recipe saveRecipe(Post post, Dish dish, ModerationStatus status) {
    Recipe recipe =
        Recipe.builder()
            .dish(dish)
            .user(member)
            .name(dish.getName())
            .instructions("Nấu chín, nêm vừa ăn")
            .servings(2)
            .moderationStatus(status)
            .posts(new HashSet<>(Set.of(post)))
            .build();
    return recipeRepository.saveAndFlush(recipe);
  }

  private void linkIngredient(Recipe recipe, Ingredient ingredient) {
    recipeIngredientRepository.saveAndFlush(
        RecipeIngredient.builder()
            .recipeId(recipe.getId())
            .ingredientId(ingredient.getId())
            .amount(new BigDecimal("100"))
            .unit("g")
            .build());
  }
}
