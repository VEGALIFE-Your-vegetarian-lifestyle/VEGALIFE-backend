package com.vegalife.integration.controller.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.model.menu.MealType;
import com.vegalife.model.menu.Menu;
import com.vegalife.model.menu.MenuDetail;
import com.vegalife.model.menu.MenuStatus;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.user.User;
import com.vegalife.repository.menu.MenuDetailRepository;
import com.vegalife.repository.menu.MenuRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class MenuControllerIntegrationTest {

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

  @Autowired private ObjectMapper objectMapper;

  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

  @Autowired private UserRepository userRepository;

  @Autowired private MenuRepository menuRepository;

  @Autowired private MenuDetailRepository menuDetailRepository;

  @Autowired private DishRepository dishRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User user;
  private User otherUser;
  private String accessToken;

  @BeforeEach
  void setUp() {
    user = createUser("menuowner", "menuowner@example.com");
    otherUser = createUser("othermenu", "othermenu@example.com");
    accessToken = jwtTokenService.generateAccessToken(user);
  }

  @Test
  void listMenus_monthReturnsMenusIntersectingThatMonth() throws Exception {
    createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2026, 11, 2), MenuStatus.drafted);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "month")
                .param("month", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Menus retrieved successfully"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].startDate").value("2026-10-05"));
  }

  @Test
  void listMenus_weekReturnsMenusIntersectingThatIsoWeek() throws Exception {
    createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2026, 10, 12), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "week")
                .param("date", "2026-10-07"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].startDate").value("2026-10-05"));
  }

  @Test
  void listMenus_customRangeReturnsMenusIntersectingTheWindow() throws Exception {
    createMenu(user, LocalDate.of(2026, 9, 28), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2026, 10, 12), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "custom")
                .param("from", "2026-09-01")
                .param("to", "2026-10-15"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void listMenus_menuStraddlingMonthBoundaryAppearsInBothMonths() throws Exception {
    createMenu(user, LocalDate.of(2026, 9, 28), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "month")
                .param("month", "2026-09"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "month")
                .param("month", "2026-10"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void listMenus_statusCombinesWithWindow() throws Exception {
    createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2026, 10, 12), MenuStatus.drafted);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "month")
                .param("month", "2026-10")
                .param("status", "scheduled"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].status").value("scheduled"));
  }

  @Test
  void listMenus_isScopedToTheAuthenticatedUser() throws Exception {
    createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    createMenu(otherUser, LocalDate.of(2026, 10, 12), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "month")
                .param("month", "2026-10"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].startDate").value("2026-10-05"));
  }

  @Test
  void listMenus_defaultsToStartDateDescending() throws Exception {
    createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2026, 10, 12), MenuStatus.drafted);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "month")
                .param("month", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].startDate").value("2026-10-12"))
        .andExpect(jsonPath("$.data.content[1].startDate").value("2026-10-05"));
  }

  @Test
  void listMenus_withoutPeriodReturnsEverything() throws Exception {
    createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2025, 1, 6), MenuStatus.completed);

    mockMvc
        .perform(get("/api/menus").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void listMenus_customFromAfterToReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "custom")
                .param("from", "2026-10-15")
                .param("to", "2026-09-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void listMenus_customWithoutBoundsReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "custom")
                .param("from", "2026-09-01"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listMenus_malformedDateReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "week")
                .param("date", "2026-13-40"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listMenus_unknownPeriodReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "quarter"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listMenus_withoutJwtReturns401() throws Exception {
    mockMvc.perform(get("/api/menus")).andExpect(status().isUnauthorized());
  }

  @Test
  void listMenus_invalidSizeReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/menus").header("Authorization", "Bearer " + accessToken).param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getMenu_returnsOwnedMenuWithDaysAndMeals() throws Exception {
    Menu menu = createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    Dish dish = dishRepository.save(Dish.builder().name("Tofu scramble").build());
    menuDetailRepository.save(
        MenuDetail.builder()
            .menuId(menu.getId())
            .dish(dish)
            .date(LocalDate.of(2026, 10, 5))
            .mealType(MealType.BREAKFAST)
            .servings(2)
            .build());

    mockMvc
        .perform(
            get("/api/menus/{menuId}", menu.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Menu retrieved successfully"))
        .andExpect(jsonPath("$.data.id").value(menu.getId().toString()))
        .andExpect(jsonPath("$.data.status").value("scheduled"))
        .andExpect(jsonPath("$.data.days.length()").value(1))
        .andExpect(jsonPath("$.data.days[0].date").value("2026-10-05"))
        .andExpect(jsonPath("$.data.days[0].meals[0].mealType").value("BREAKFAST"))
        .andExpect(jsonPath("$.data.days[0].meals[0].dishName").value("Tofu scramble"))
        .andExpect(jsonPath("$.data.days[0].meals[0].servings").value(2));
  }

  @Test
  void getMenu_foreignOrUnknownIdReturns404() throws Exception {
    Menu otherMenu = createMenu(otherUser, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus/{menuId}", otherMenu.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Menu not found"));
    mockMvc
        .perform(
            get("/api/menus/{menuId}", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void getMenu_withoutJwtReturns401() throws Exception {
    mockMvc
        .perform(get("/api/menus/{menuId}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getMenu_ofMenuWithNoDetailsReturnsEmptyDays() throws Exception {
    Menu menu = createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.drafted);

    mockMvc
        .perform(
            get("/api/menus/{menuId}", menu.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days.length()").value(0));
  }

  @Test
  void createMenu_returns201WithDraftedMenuAndPersistsDays() throws Exception {
    List<UUID> dishes = createDishes(7);

    mockMvc
        .perform(
            post("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMenuJson(MONDAY, oneBreakfastPerDay(dishes))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Menu created successfully"))
        .andExpect(jsonPath("$.data.status").value("drafted"))
        .andExpect(jsonPath("$.data.startDate").value("2026-10-05"))
        .andExpect(jsonPath("$.data.endDate").value("2026-10-11"))
        .andExpect(jsonPath("$.data.days.length()").value(7))
        .andExpect(jsonPath("$.data.days[0].meals[0].mealType").value("BREAKFAST"))
        .andExpect(jsonPath("$.data.days[0].meals[0].servings").value(2));

    assertThat(menuRepository.count()).isEqualTo(1);
    assertThat(menuDetailRepository.findAll()).hasSize(7);
  }

  @Test
  void createMenu_isOwnedByTheAuthenticatedCallerOnly() throws Exception {
    List<UUID> dishes = createDishes(7);

    mockMvc
        .perform(
            post("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMenuJson(MONDAY, oneBreakfastPerDay(dishes))))
        .andExpect(status().isCreated());

    Menu created = menuRepository.findAll().get(0);
    assertThat(created.getUserId()).isEqualTo(user.getId());
    assertThat(created.getUserId()).isNotEqualTo(otherUser.getId());
  }

  @Test
  void createMenu_duplicateMealTypeReturns400AndPersistsNothing() throws Exception {
    List<UUID> dishes = createDishes(7);
    List<Map<String, Object>> days = new ArrayList<>(oneBreakfastPerDay(dishes));
    days.set(
        0,
        day(
            "2026-10-05",
            List.of(meal("BREAKFAST", dishes.get(0), 2), meal("BREAKFAST", dishes.get(1), 1))));

    mockMvc
        .perform(
            post("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMenuJson(MONDAY, days)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("a date may have at most one meal per mealType"));

    assertThat(menuRepository.count()).isZero();
  }

  @Test
  void createMenu_unknownDishReturns400() throws Exception {
    List<Map<String, Object>> days = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      days.add(day(dateOf(i), List.of(meal("BREAKFAST", UUID.randomUUID(), 2))));
    }

    mockMvc
        .perform(
            post("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMenuJson(MONDAY, days)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("dish not found or deleted")));

    assertThat(menuRepository.count()).isZero();
  }

  @Test
  void createMenu_dishRepeatedTooOftenReturns400() throws Exception {
    List<UUID> dishes = createDishes(7);
    UUID favourite = dishes.get(0);
    List<Map<String, Object>> days = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      days.add(day(dateOf(i), List.of(meal("BREAKFAST", favourite, 2))));
    }

    mockMvc
        .perform(
            post("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMenuJson(MONDAY, days)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message").value("the same dish may appear at most 3 times in a menu"));

    assertThat(menuRepository.count()).isZero();
  }

  @Test
  void createMenu_tooFewDistinctDishesReturns400() throws Exception {
    List<UUID> dishes = createDishes(4);
    List<Map<String, Object>> days = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      days.add(day(dateOf(i), List.of(meal("BREAKFAST", dishes.get(i % 4), 2))));
    }

    mockMvc
        .perform(
            post("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMenuJson(MONDAY, days)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("a menu must reference at least 5 distinct dishes"));
  }

  @Test
  void createMenu_missingStartDateReturns400() throws Exception {
    List<UUID> dishes = createDishes(7);
    Map<String, Object> body = Map.of("days", oneBreakfastPerDay(dishes));

    mockMvc
        .perform(
            post("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());

    assertThat(menuRepository.count()).isZero();
  }

  @Test
  void createMenu_withoutJwtReturns401() throws Exception {
    mockMvc
        .perform(
            post("/api/menus")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMenuJson(MONDAY, oneBreakfastPerDay(createDishes(7)))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateMenu_onScheduledMenuReturns200AndPersistsChanges() throws Exception {
    Menu menu = createMenu(user, MONDAY, MenuStatus.scheduled);
    List<UUID> dishes = createDishes(8);

    mockMvc
        .perform(
            patch("/api/menus/{menuId}", menu.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchMenuJson(oneBreakfastPerDay(dishes.subList(0, 7)), "soy-free", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Menu updated successfully"))
        .andExpect(jsonPath("$.data.status").value("scheduled"))
        .andExpect(jsonPath("$.data.startDate").value("2026-10-05"))
        .andExpect(jsonPath("$.data.endDate").value("2026-10-11"))
        .andExpect(jsonPath("$.data.dietaryPreferences").value("soy-free"))
        .andExpect(jsonPath("$.data.days.length()").value(7));

    assertThat(menuDetailRepository.findAll()).hasSize(7);
  }

  @Test
  void updateMenu_replacesExistingDaysWholesale() throws Exception {
    Menu menu = createMenu(user, MONDAY, MenuStatus.drafted);
    Dish oldDish = dishRepository.save(Dish.builder().name("Old dish").build());
    menuDetailRepository.save(
        MenuDetail.builder()
            .menuId(menu.getId())
            .dish(oldDish)
            .date(MONDAY)
            .mealType(MealType.BREAKFAST)
            .servings(1)
            .build());
    List<UUID> dishes = createDishes(7);

    mockMvc
        .perform(
            patch("/api/menus/{menuId}", menu.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchMenuJson(oneBreakfastPerDay(dishes), null, "new notes")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notes").value("new notes"))
        .andExpect(jsonPath("$.data.dietaryPreferences").value(nullValue()))
        .andExpect(jsonPath("$.data.days.length()").value(7));

    assertThat(menuDetailRepository.findAll()).hasSize(7);
  }

  @Test
  void updateMenu_cancelledOrCompletedReturns409() throws Exception {
    for (MenuStatus status : List.of(MenuStatus.cancelled, MenuStatus.completed)) {
      Menu menu = createMenu(user, MONDAY, status);

      mockMvc
          .perform(
              patch("/api/menus/{menuId}", menu.getId())
                  .header("Authorization", "Bearer " + accessToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(patchMenuJson(oneBreakfastPerDay(createDishes(7)), null, null)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value("Only drafted or scheduled menus can be updated"));
    }
  }

  @Test
  void updateMenu_foreignMenuReturns404() throws Exception {
    Menu menu = createMenu(otherUser, MONDAY, MenuStatus.drafted);

    mockMvc
        .perform(
            patch("/api/menus/{menuId}", menu.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchMenuJson(oneBreakfastPerDay(createDishes(7)), null, null)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Menu not found"));
  }

  @Test
  void updateMenu_invalidResultingWeekReturns400AndKeepsExistingDays() throws Exception {
    Menu menu = createMenu(user, MONDAY, MenuStatus.drafted);
    Dish oldDish = dishRepository.save(Dish.builder().name("Kept dish").build());
    menuDetailRepository.save(
        MenuDetail.builder()
            .menuId(menu.getId())
            .dish(oldDish)
            .date(MONDAY)
            .mealType(MealType.BREAKFAST)
            .servings(1)
            .build());
    UUID favourite = createDishes(1).get(0);
    List<Map<String, Object>> days = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      days.add(day(dateOf(i), List.of(meal("BREAKFAST", favourite, 2))));
    }

    mockMvc
        .perform(
            patch("/api/menus/{menuId}", menu.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchMenuJson(days, null, null)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message").value("the same dish may appear at most 3 times in a menu"));

    assertThat(menuDetailRepository.findAll()).hasSize(1);
  }

  @Test
  void updateMenu_missingDaysReturns400() throws Exception {
    Menu menu = createMenu(user, MONDAY, MenuStatus.drafted);

    mockMvc
        .perform(
            patch("/api/menus/{menuId}", menu.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dietaryPreferences\": \"vegan\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateMenu_withoutJwtReturns401() throws Exception {
    Menu menu = createMenu(user, MONDAY, MenuStatus.drafted);

    mockMvc
        .perform(
            patch("/api/menus/{menuId}", menu.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchMenuJson(oneBreakfastPerDay(createDishes(7)), null, null)))
        .andExpect(status().isUnauthorized());
  }

  private int dishSequence;

  private List<UUID> createDishes(int count) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      // Active dish names are unique (V22); every dish needs its own name.
      ids.add(
          dishRepository
              .save(Dish.builder().name("Menu dish " + (++dishSequence)).build())
              .getId());
    }
    return ids;
  }

  private static String dateOf(int dayOffset) {
    return MONDAY.plusDays(dayOffset).toString();
  }

  /** One BREAKFAST meal per day, each with its own dish — the minimal valid week. */
  private static List<Map<String, Object>> oneBreakfastPerDay(List<UUID> dishes) {
    List<Map<String, Object>> days = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      days.add(day(dateOf(i), List.of(meal("BREAKFAST", dishes.get(i), 2))));
    }
    return days;
  }

  private static Map<String, Object> day(String date, List<Map<String, Object>> meals) {
    Map<String, Object> day = new LinkedHashMap<>();
    day.put("date", date);
    day.put("meals", meals);
    return day;
  }

  private static Map<String, Object> meal(String mealType, UUID dishId, int servings) {
    Map<String, Object> meal = new LinkedHashMap<>();
    meal.put("mealType", mealType);
    meal.put("dishId", dishId.toString());
    meal.put("servings", servings);
    return meal;
  }

  private String createMenuJson(LocalDate startDate, List<Map<String, Object>> days)
      throws JsonProcessingException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("startDate", startDate.toString());
    body.put("days", days);
    return objectMapper.writeValueAsString(body);
  }

  private String patchMenuJson(
      List<Map<String, Object>> days, String dietaryPreferences, String notes)
      throws JsonProcessingException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("days", days);
    if (dietaryPreferences != null) {
      body.put("dietaryPreferences", dietaryPreferences);
    }
    if (notes != null) {
      body.put("notes", notes);
    }
    return objectMapper.writeValueAsString(body);
  }

  private User createUser(String username, String email) {
    return userRepository.save(
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build());
  }

  private Menu createMenu(User owner, LocalDate start, MenuStatus status) {
    Menu menu =
        menuRepository.save(
            Menu.builder()
                .userId(owner.getId())
                .startDate(start)
                .endDate(start.plusDays(6))
                .status(status)
                .build());
    assertThat(menu.getId()).isNotNull();
    return menu;
  }
}
