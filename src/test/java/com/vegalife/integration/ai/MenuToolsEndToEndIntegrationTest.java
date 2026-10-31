package com.vegalife.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vegalife.enums.menu.MenuStatus;
import com.vegalife.model.menu.Menu;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.user.User;
import com.vegalife.repository.menu.MenuRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.ai.AiToolContext;
import com.vegalife.service.ai.MenuTools;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end coverage of the menu tool surface the AI chat uses (ADR-013), replacing the
 * two-container {@code McpMenuEndToEndIntegrationTest} this refactor deletes.
 *
 * <p>Where that test drove the MCP server's {@code /mcp} endpoint over HTTP, this one drives the
 * same {@link ToolCallback}s the chat client actually holds: {@link MenuTools} is a real bean, the
 * callbacks are built with {@link MethodToolCallbackProvider} exactly as {@code
 * AiChatService.initChatClient} builds them, and every call goes through {@code
 * ToolCallback.call(String, ToolContext)} — the invocation path Spring AI uses, including the
 * result converter. That closes the loop the unit tests cannot: the JSON the model receives is
 * single-encoded, and identity arrives only through the {@link ToolContext}.
 *
 * <p>What is carried over from the Docker test is its whole point — two distinct callers, with the
 * database as the referee (BR-MCP-004, BR-MCP-005).
 *
 * <p>Result shape, unchanged from the MCP days: a success is the backend's {@code {success,
 * message, data}} envelope as a JSON object; a failure is bare {@code "error: ..."} text, so the
 * model can read it without parsing (BR-MCP-007). The two shapes are asserted separately here.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class MenuToolsEndToEndIntegrationTest {

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

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private MenuTools menuTools;
  @Autowired private UserRepository userRepository;
  @Autowired private DishRepository dishRepository;
  @Autowired private MenuRepository menuRepository;

  private ToolCallback[] tools;

  private User owner;
  private User stranger;
  private List<UUID> dishIds;

  /** Monday of the server-UTC week, the only window BR-MCP-005 lets update_menu touch. */
  private LocalDate currentWeekMonday;

  @BeforeEach
  void setUp() {
    // The same construction AiChatService performs at startup.
    tools = MethodToolCallbackProvider.builder().toolObjects(menuTools).build().getToolCallbacks();

    owner = createUser();
    stranger = createUser();
    dishIds = seedDishes(5);
    currentWeekMonday =
        LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  // ------------------------------------------------------------------ the tool surface

  @Test
  void theSurfaceIsTheSixDocumentedTools() {
    List<String> names =
        Arrays.stream(tools).map(tool -> tool.getToolDefinition().name()).sorted().toList();

    // Five menu tools plus suggest_dishes, the read-only dish browser (issue #163).
    assertThat(names)
        .containsExactlyInAnyOrder(
            "create_menu",
            "get_menu",
            "get_meal_plan",
            "schedule_menu",
            "suggest_dishes",
            "update_menu");
  }

  @Test
  void theModelVisibleSchemaCarriesNoIdentityOrCredentialParameter() {
    for (ToolCallback tool : tools) {
      String name = tool.getToolDefinition().name();

      assertThat(tool.getToolDefinition().description())
          .as("tool %s must describe itself", name)
          .isNotBlank()
          .contains("caller");

      // BR-MCP-004 at the schema level: who is calling is context, never an argument the
      // model could forge or be handed.
      assertThat(tool.getToolDefinition().inputSchema().toLowerCase())
          .as("tool %s must not expose identity as a parameter", name)
          .doesNotContain("jwt", "token", "password", "authorization", "userid");
    }
  }

  // ------------------------------------------------------------------ the happy path

  @Test
  void createMenuThroughTheToolPersistsADraftForTheCaller() throws Exception {
    JsonNode created =
        parseSuccess(
            call("create_menu", contextFor(owner), createMenuArguments(currentWeekMonday)));

    assertThat(created.path("message").asText()).isEqualTo("Menu created successfully");
    String menuId = created.path("data").path("id").asText();
    assertThat(menuId).isNotBlank();

    Menu persisted = menuRepository.findById(UUID.fromString(menuId)).orElseThrow();
    assertThat(persisted.getUserId())
        .as("the drafted menu must be persisted in Postgres for the caller")
        .isEqualTo(owner.getId());
    assertThat(persisted.getStartDate()).isEqualTo(currentWeekMonday);
    assertThat(persisted.getStatus()).isEqualTo(MenuStatus.drafted);

    // A deep read with the same identity returns the menu it just wrote, meals and dishes intact.
    JsonNode readBack =
        parseSuccess(call("get_meal_plan", contextFor(owner), "{\"menuId\":\"" + menuId + "\"}"));
    assertThat(readBack.path("message").asText()).isEqualTo("Meal plan retrieved successfully");
    assertThat(readBack.toString()).contains(menuId);
    assertThat(readBack.path("data").get(0).path("days").get(0).path("meals").get(0).path("dish"))
        .isNotNull();
  }

  // ------------------------------------------------------------------ caller isolation

  @Test
  void aSecondCallerSeesNoneOfTheFirstCallersMenus() throws Exception {
    String menuId =
        parseSuccess(call("create_menu", contextFor(owner), createMenuArguments(currentWeekMonday)))
            .path("data")
            .path("id")
            .asText();

    // An unbounded list read: were identity not scoping the query, the stranger's menu list would
    // contain the owner's menu. It must come back empty instead.
    JsonNode strangerList = parseSuccess(call("get_menu", contextFor(stranger), "{}"));
    assertThat(strangerList.path("data").path("totalElements").asInt())
        .as("a caller's get_menu must never surface another member's menus")
        .isZero();

    // Reading the other member's menu by id is a readable 404, not a leak.
    assertThat(call("get_meal_plan", contextFor(stranger), "{\"menuId\":\"" + menuId + "\"}"))
        .isEqualTo("error: Menu not found");

    // The owner still sees exactly their own menu.
    JsonNode ownerList = parseSuccess(call("get_menu", contextFor(owner), "{}"));
    assertThat(ownerList.path("data").path("totalElements").asInt()).isEqualTo(1);
  }

  @Test
  void aSecondCallerCannotUpdateOrScheduleTheFirstCallersMenu() throws Exception {
    String menuId =
        parseSuccess(call("create_menu", contextFor(owner), createMenuArguments(currentWeekMonday)))
            .path("data")
            .path("id")
            .asText();

    assertThat(call("update_menu", contextFor(stranger), updateMenuArguments(menuId)))
        .as("update_menu must refuse a menu the caller does not own (BR-MCP-005)")
        .startsWith("update refused");

    assertThat(call("schedule_menu", contextFor(stranger), "{\"menuId\":\"" + menuId + "\"}"))
        .isEqualTo("error: Menu not found");

    Menu untouched = menuRepository.findById(UUID.fromString(menuId)).orElseThrow();
    assertThat(untouched.getUserId()).isEqualTo(owner.getId());
    assertThat(untouched.getStatus())
        .as("neither refusal may have advanced the owner's menu")
        .isEqualTo(MenuStatus.drafted);

    // The owner, by contrast, can schedule it.
    JsonNode ownerSchedule =
        parseSuccess(call("schedule_menu", contextFor(owner), "{\"menuId\":\"" + menuId + "\"}"));
    assertThat(ownerSchedule.path("message").asText())
        .isEqualTo("Menu status updated successfully");
  }

  // ------------------------------------------------------------------ identity

  @Test
  void menuToolsRefuseToRunWithoutACallerIdentity() throws Exception {
    long menusBefore = menuRepository.count();

    // forUser(null) is the only way to reach this branch: the map must be non-empty, since
    // Spring AI rejects a ToolContext-taking method handed an empty one outright.
    String result =
        call("create_menu", AiToolContext.forUser(null), createMenuArguments(currentWeekMonday));

    assertThat(result).contains("caller identity missing");
    assertThat(menuRepository.count())
        .as("no menu may be created without the caller's identity")
        .isEqualTo(menusBefore);
  }

  // ------------------------------------------------------------------ helpers

  /**
   * Drives one tool the way the chat client does and returns the raw result, which is either the
   * success envelope (JSON) or a bare {@code "error: ..."} line.
   */
  private String call(String toolName, Map<String, Object> toolContext, String arguments) {
    ToolCallback tool =
        Arrays.stream(tools)
            .filter(candidate -> candidate.getToolDefinition().name().equals(toolName))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no tool named " + toolName));

    return tool.call(arguments, new ToolContext(toolContext));
  }

  /**
   * Parses a successful result and asserts it is single-encoded on the way past: the model must
   * receive a JSON object, not a quoted string literal — the failure {@code
   * RawTextToolResultConverter} exists to prevent.
   */
  private JsonNode parseSuccess(String raw) throws JsonProcessingException {
    assertThat(raw)
        .as("a tool success must be a single-encoded JSON envelope, got: %s", raw)
        .startsWith("{");
    return MAPPER.readTree(raw);
  }

  private Map<String, Object> contextFor(User user) {
    return AiToolContext.forUser(user.getId());
  }

  /** Seven days of one breakfast each from the seeded dishes (BR-MENU-005 satisfied). */
  private String createMenuArguments(LocalDate startDate) throws JsonProcessingException {
    ObjectNode arguments = MAPPER.createObjectNode();
    arguments.put("startDate", startDate.toString());
    arguments.put("notes", "Test week summary");
    arguments.set("days", sevenBreakfastDays(startDate));
    return MAPPER.writeValueAsString(arguments);
  }

  private String updateMenuArguments(String menuId) throws JsonProcessingException {
    ObjectNode arguments = MAPPER.createObjectNode();
    arguments.put("menuId", menuId);
    arguments.put("notes", "Updated week summary");
    arguments.set("days", sevenBreakfastDays(currentWeekMonday));
    return MAPPER.writeValueAsString(arguments);
  }

  private ArrayNode sevenBreakfastDays(LocalDate startDate) {
    ArrayNode days = MAPPER.createArrayNode();
    for (int day = 0; day < 7; day++) {
      ObjectNode dayNode = days.addObject();
      dayNode.put("date", startDate.plusDays(day).toString());
      ObjectNode meal = dayNode.putArray("meals").addObject();
      meal.put("mealType", "BREAKFAST");
      meal.put("dishId", dishIds.get(day % dishIds.size()).toString());
      meal.put("servings", 2);
    }
    return days;
  }

  private User createUser() {
    String unique = UUID.randomUUID().toString().substring(0, 8);
    return userRepository.save(
        User.builder()
            .username("tools" + unique)
            .email(unique + "@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build());
  }

  private List<UUID> seedDishes(int count) {
    List<UUID> ids = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      ids.add(dishRepository.save(Dish.builder().name("Tool E2E Dish " + i).build()).getId());
    }
    return ids;
  }
}
