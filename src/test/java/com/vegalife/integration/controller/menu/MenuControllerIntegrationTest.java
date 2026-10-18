package com.vegalife.integration.controller.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import java.time.ZoneOffset;
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
  void listMenus_rangeReturnsMenusIntersectingThatRange() throws Exception {
    createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2026, 11, 2), MenuStatus.drafted);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2026-10-01")
                .param("to", "2026-10-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Menus retrieved successfully"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].startDate").value("2026-10-05"));
  }

  @Test
  void listMenus_oneWeekRangeReturnsMenusIntersectingThatWeek() throws Exception {
    createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2026, 10, 12), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2026-10-05")
                .param("to", "2026-10-11"))
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
                .param("from", "2026-09-01")
                .param("to", "2026-09-30"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2026-10-01")
                .param("to", "2026-10-31"))
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
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
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
                .param("from", "2026-10-01")
                .param("to", "2026-10-31"))
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
                .param("from", "2026-10-01")
                .param("to", "2026-10-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].startDate").value("2026-10-12"))
        .andExpect(jsonPath("$.data.content[1].startDate").value("2026-10-05"));
  }

  @Test
  void listMenus_withoutBoundsReturnsEverything() throws Exception {
    createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2025, 1, 6), MenuStatus.completed);

    mockMvc
        .perform(get("/api/menus").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void listMenus_fromAloneBoundsOnlyFromBelow() throws Exception {
    createMenu(user, LocalDate.of(2026, 9, 7), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2026, 11, 2), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2026-10-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].startDate").value("2026-11-02"));
  }

  @Test
  void listMenus_toAloneBoundsOnlyFromAbove() throws Exception {
    createMenu(user, LocalDate.of(2026, 9, 7), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2026, 11, 2), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("to", "2026-10-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].startDate").value("2026-09-07"));
  }

  @Test
  void listMenus_legacyPeriodDateAndMonthParamsAreIgnored() throws Exception {
    createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2026, 11, 2), MenuStatus.drafted);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("period", "month")
                .param("month", "2026-10")
                .param("date", "2026-10-07"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void listMenus_customFromAfterToReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2026-10-15")
                .param("to", "2026-09-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value(containsString("from must be on or before to")));
  }

  @Test
  void listMenus_malformedFromReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2026-13-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value(containsString("Invalid from")));
  }

  @Test
  void listMenus_malformedToReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("to", "2026-13-40"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value(containsString("Invalid to")));
  }

  @Test
  void listMenus_rangeLongerThan366DaysReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2026-01-01")
                .param("to", "2027-01-03"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("must not exceed 366 days")));
  }

  @Test
  void listMenus_rangeOfExactly366DaysIsAccepted() throws Exception {
    createMenu(user, LocalDate.of(2026, 1, 5), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2026-01-01")
                .param("to", "2027-01-02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].startDate").value("2026-01-05"));
  }

  @Test
  void listMenus_oneSidedWindowIsNeverLengthChecked() throws Exception {
    createMenu(user, LocalDate.of(2026, 1, 5), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2015-01-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void listMenus_unknownStatusReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/menus")
                .header("Authorization", "Bearer " + accessToken)
                .param("status", "archived"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("Unknown status")));
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
  void getMealPlans_menuIdReturnsOwnedMenuWithDaysAndMeals() throws Exception {
    Menu menu = createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    Dish dish =
        dishRepository.save(
            Dish.builder()
                .name("Tofu scramble")
                .description("Crumble with turmeric and black salt")
                .imageUrl("https://example.com/tofu-scramble.jpg")
                .cuisineType("American")
                .build());
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
            get("/api/menus/details")
                .header("Authorization", "Bearer " + accessToken)
                .param("menuId", menu.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Meal plan retrieved successfully"))
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(menu.getId().toString()))
        .andExpect(jsonPath("$.data[0].startDate").value("2026-10-05"))
        .andExpect(jsonPath("$.data[0].endDate").value("2026-10-11"))
        .andExpect(jsonPath("$.data[0].status").value("scheduled"))
        .andExpect(jsonPath("$.data[0].days.length()").value(1))
        .andExpect(jsonPath("$.data[0].days[0].date").value("2026-10-05"))
        .andExpect(jsonPath("$.data[0].days[0].meals[0].mealType").value("BREAKFAST"))
        .andExpect(jsonPath("$.data[0].days[0].meals[0].servings").value(2))
        .andExpect(jsonPath("$.data[0].days[0].meals[0].dish.id").value(dish.getId().toString()))
        .andExpect(jsonPath("$.data[0].days[0].meals[0].dish.name").value("Tofu scramble"))
        .andExpect(
            jsonPath("$.data[0].days[0].meals[0].dish.description")
                .value("Crumble with turmeric and black salt"))
        .andExpect(
            jsonPath("$.data[0].days[0].meals[0].dish.imageUrl")
                .value("https://example.com/tofu-scramble.jpg"))
        .andExpect(jsonPath("$.data[0].days[0].meals[0].dish.cuisineType").value("American"));
  }

  @Test
  void getMealPlans_foreignOrUnknownIdReturns404() throws Exception {
    Menu otherMenu = createMenu(otherUser, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus/details")
                .header("Authorization", "Bearer " + accessToken)
                .param("menuId", otherMenu.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Menu not found"));
    mockMvc
        .perform(
            get("/api/menus/details")
                .header("Authorization", "Bearer " + accessToken)
                .param("menuId", UUID.randomUUID().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Menu not found"));
  }

  @Test
  void getMealPlans_withoutJwtReturns401() throws Exception {
    mockMvc.perform(get("/api/menus/details")).andExpect(status().isUnauthorized());
  }

  @Test
  void getIndividualMenuRouteHasNoGetHandler() throws Exception {
    // FR-009: GET /api/menus/{menuId} was deleted; /details?menuId= is its replacement.
    // The path itself stays registered for PATCH and POST .../status, so Spring answers
    // 405 Method Not Allowed rather than 404 — the GET method is what's gone.
    Menu menu = createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.drafted);

    mockMvc
        .perform(
            get("/api/menus/{menuId}", menu.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void getMealPlans_menuWithNoDetailsReturnsEmptyDays() throws Exception {
    Menu menu = createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.drafted);

    mockMvc
        .perform(
            get("/api/menus/details")
                .header("Authorization", "Bearer " + accessToken)
                .param("menuId", menu.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].days").isArray())
        .andExpect(jsonPath("$.data[0].days").isEmpty());
  }

  @Test
  void getMealPlans_defaultsToTheCurrentIsoWeek() throws Exception {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Menu currentWeek = createMenu(user, today.minusDays(3), MenuStatus.scheduled);
    createMenu(user, today.minusWeeks(20), MenuStatus.scheduled);

    mockMvc
        .perform(get("/api/menus/details").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(currentWeek.getId().toString()))
        .andExpect(jsonPath("$.data[0].startDate").value(currentWeek.getStartDate().toString()));
  }

  @Test
  void getMealPlans_fromAloneBoundsOnlyFromBelow() throws Exception {
    createMenu(user, LocalDate.of(2026, 9, 7), MenuStatus.scheduled);
    Menu afterFrom = createMenu(user, LocalDate.of(2026, 11, 2), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus/details")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2026-10-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(afterFrom.getId().toString()))
        .andExpect(jsonPath("$.data[0].startDate").value("2026-11-02"));
  }

  @Test
  void getMealPlans_toAloneBoundsOnlyFromAbove() throws Exception {
    Menu beforeTo = createMenu(user, LocalDate.of(2026, 9, 7), MenuStatus.scheduled);
    createMenu(user, LocalDate.of(2026, 11, 2), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus/details")
                .header("Authorization", "Bearer " + accessToken)
                .param("to", "2026-10-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(beforeTo.getId().toString()))
        .andExpect(jsonPath("$.data[0].startDate").value("2026-09-07"));
  }

  @Test
  void getMealPlans_rangeReturnsEveryIntersectingMenuWithFullDetail() throws Exception {
    Menu first = createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    Menu second = createMenu(user, LocalDate.of(2026, 10, 12), MenuStatus.scheduled);
    Dish firstDish =
        dishRepository.save(
            Dish.builder()
                .name("Early October bowl")
                .description("Roasted squash and quinoa")
                .imageUrl("https://example.com/early-october.jpg")
                .cuisineType("Mexican")
                .build());
    Dish secondDish =
        dishRepository.save(
            Dish.builder()
                .name("Late October bowl")
                .description("Mushroom and barley")
                .imageUrl("https://example.com/late-october.jpg")
                .cuisineType("Italian")
                .build());
    menuDetailRepository.save(
        MenuDetail.builder()
            .menuId(first.getId())
            .dish(firstDish)
            .date(LocalDate.of(2026, 10, 5))
            .mealType(MealType.LUNCH)
            .servings(3)
            .build());
    menuDetailRepository.save(
        MenuDetail.builder()
            .menuId(second.getId())
            .dish(secondDish)
            .date(LocalDate.of(2026, 10, 12))
            .mealType(MealType.DINNER)
            .servings(1)
            .build());

    mockMvc
        .perform(
            get("/api/menus/details")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2026-10-01")
                .param("to", "2026-10-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Meal plan retrieved successfully"))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id").value(first.getId().toString()))
        .andExpect(jsonPath("$.data[0].startDate").value("2026-10-05"))
        .andExpect(jsonPath("$.data[0].days.length()").value(1))
        .andExpect(jsonPath("$.data[0].days[0].meals[0].mealType").value("LUNCH"))
        .andExpect(jsonPath("$.data[0].days[0].meals[0].servings").value(3))
        .andExpect(jsonPath("$.data[0].days[0].meals[0].dish.name").value("Early October bowl"))
        .andExpect(jsonPath("$.data[1].id").value(second.getId().toString()))
        .andExpect(jsonPath("$.data[1].startDate").value("2026-10-12"))
        .andExpect(jsonPath("$.data[1].days.length()").value(1))
        .andExpect(jsonPath("$.data[1].days[0].meals[0].mealType").value("DINNER"))
        .andExpect(jsonPath("$.data[1].days[0].meals[0].servings").value(1))
        .andExpect(jsonPath("$.data[1].days[0].meals[0].dish.name").value("Late October bowl"))
        .andExpect(jsonPath("$.data[1].days[0].meals[0].dish.cuisineType").value("Italian"));
  }

  @Test
  void getMealPlans_menuIdOverridesTheSuppliedRange() throws Exception {
    Menu inRange = createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    Menu outsideRange = createMenu(user, LocalDate.of(2025, 1, 6), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus/details")
                .header("Authorization", "Bearer " + accessToken)
                .param("menuId", outsideRange.getId().toString())
                .param("from", "2026-10-01")
                .param("to", "2026-10-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(outsideRange.getId().toString()))
        .andExpect(jsonPath("$.data[0].startDate").value("2025-01-06"));

    assertThat(inRange.getStartDate()).isAfterOrEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(outsideRange.getEndDate()).isBefore(LocalDate.of(2026, 10, 1));
  }

  @Test
  void getMealPlans_malformedMenuIdReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/menus/details")
                .header("Authorization", "Bearer " + accessToken)
                .param("menuId", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void getMealPlans_isScopedToTheAuthenticatedUser() throws Exception {
    Menu mine = createMenu(user, LocalDate.of(2026, 10, 5), MenuStatus.scheduled);
    Menu theirs = createMenu(otherUser, LocalDate.of(2026, 10, 12), MenuStatus.scheduled);

    mockMvc
        .perform(
            get("/api/menus/details")
                .header("Authorization", "Bearer " + accessToken)
                .param("menuId", theirs.getId().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Menu not found"));

    mockMvc
        .perform(
            get("/api/menus/details")
                .header("Authorization", "Bearer " + accessToken)
                .param("from", "2026-10-01")
                .param("to", "2026-10-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(mine.getId().toString()))
        .andExpect(jsonPath("$.data[0].startDate").value("2026-10-05"));
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
                .content(patchMenuJson(oneBreakfastPerDay(dishes.subList(0, 7)), "soy-free")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Menu updated successfully"))
        .andExpect(jsonPath("$.data.status").value("scheduled"))
        .andExpect(jsonPath("$.data.startDate").value("2026-10-05"))
        .andExpect(jsonPath("$.data.endDate").value("2026-10-11"))
        .andExpect(jsonPath("$.data.notes").value("soy-free"))
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
                .content(patchMenuJson(oneBreakfastPerDay(dishes), "new notes")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notes").value("new notes"))
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
                  .content(patchMenuJson(oneBreakfastPerDay(createDishes(7)), null)))
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
                .content(patchMenuJson(oneBreakfastPerDay(createDishes(7)), null)))
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
                .content(patchMenuJson(days, null)))
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
                .content("{\"notes\": \"vegan\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateMenu_withoutJwtReturns401() throws Exception {
    Menu menu = createMenu(user, MONDAY, MenuStatus.drafted);

    mockMvc
        .perform(
            patch("/api/menus/{menuId}", menu.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchMenuJson(oneBreakfastPerDay(createDishes(7)), null)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void changeMenuStatus_draftToScheduledReturns200DetailAndPersists() throws Exception {
    Menu menu = createMenu(user, MONDAY, MenuStatus.drafted);

    mockMvc
        .perform(
            post("/api/menus/{menuId}/status", menu.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusJson("scheduled")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Menu status updated successfully"))
        .andExpect(jsonPath("$.data.id").value(menu.getId().toString()))
        .andExpect(jsonPath("$.data.status").value("scheduled"))
        .andExpect(jsonPath("$.data.startDate").value("2026-10-05"))
        .andExpect(jsonPath("$.data.endDate").value("2026-10-11"))
        .andExpect(jsonPath("$.data.days.length()").value(0));

    assertThat(menuRepository.findById(menu.getId()).orElseThrow().getStatus())
        .isEqualTo(MenuStatus.scheduled);
  }

  @Test
  void changeMenuStatus_scheduledToCompletedReturns200() throws Exception {
    Menu menu = createMenu(user, MONDAY, MenuStatus.scheduled);

    mockMvc
        .perform(
            post("/api/menus/{menuId}/status", menu.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusJson("completed")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("completed"));

    assertThat(menuRepository.findById(menu.getId()).orElseThrow().getStatus())
        .isEqualTo(MenuStatus.completed);
  }

  @Test
  void changeMenuStatus_cancellingFromDraftOrScheduledReturns200() throws Exception {
    for (MenuStatus from : List.of(MenuStatus.drafted, MenuStatus.scheduled)) {
      Menu menu = createMenu(user, MONDAY.plusWeeks(from.ordinal()), from);

      mockMvc
          .perform(
              post("/api/menus/{menuId}/status", menu.getId())
                  .header("Authorization", "Bearer " + accessToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(statusJson("cancelled")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("cancelled"));
    }
  }

  @Test
  void changeMenuStatus_illegalTransitionsReturn409AndChangeNothing() throws Exception {
    record Case(MenuStatus from, String target) {}
    List<Case> illegal =
        List.of(
            new Case(MenuStatus.drafted, "completed"),
            new Case(MenuStatus.scheduled, "scheduled"),
            new Case(MenuStatus.cancelled, "scheduled"),
            new Case(MenuStatus.completed, "cancelled"));
    for (Case c : illegal) {
      Menu menu = createMenu(user, MONDAY, c.from());

      mockMvc
          .perform(
              post("/api/menus/{menuId}/status", menu.getId())
                  .header("Authorization", "Bearer " + accessToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(statusJson(c.target())))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.message").value(containsString("Cannot transition a")));

      assertThat(menuRepository.findById(menu.getId()).orElseThrow().getStatus())
          .isEqualTo(c.from());
    }
  }

  @Test
  void changeMenuStatus_schedulingAnOverlappingWeekReturns409AndKeepsDrafted() throws Exception {
    createMenu(user, MONDAY, MenuStatus.scheduled);
    Menu draft = createMenu(user, MONDAY, MenuStatus.drafted);

    mockMvc
        .perform(
            post("/api/menus/{menuId}/status", draft.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusJson("scheduled")))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message").value("Another scheduled menu already overlaps this week"));

    assertThat(menuRepository.findById(draft.getId()).orElseThrow().getStatus())
        .isEqualTo(MenuStatus.drafted);
  }

  @Test
  void changeMenuStatus_schedulingAnAdjacentWeekIsAllowed() throws Exception {
    createMenu(user, MONDAY, MenuStatus.scheduled);
    Menu adjacent = createMenu(user, MONDAY.plusWeeks(1), MenuStatus.drafted);

    mockMvc
        .perform(
            post("/api/menus/{menuId}/status", adjacent.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusJson("scheduled")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("scheduled"));
  }

  @Test
  void changeMenuStatus_foreignMenuReturns404() throws Exception {
    Menu menu = createMenu(otherUser, MONDAY, MenuStatus.drafted);

    mockMvc
        .perform(
            post("/api/menus/{menuId}/status", menu.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusJson("cancelled")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Menu not found"));

    assertThat(menuRepository.findById(menu.getId()).orElseThrow().getStatus())
        .isEqualTo(MenuStatus.drafted);
  }

  @Test
  void changeMenuStatus_unknownOrDraftedTargetReturns400() throws Exception {
    Menu menu = createMenu(user, MONDAY, MenuStatus.drafted);

    for (String target : List.of("archived", "drafted")) {
      mockMvc
          .perform(
              post("/api/menus/{menuId}/status", menu.getId())
                  .header("Authorization", "Bearer " + accessToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(statusJson(target)))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.message").value(containsString("scheduled, cancelled, completed")));
    }
    assertThat(menuRepository.findById(menu.getId()).orElseThrow().getStatus())
        .isEqualTo(MenuStatus.drafted);
  }

  @Test
  void changeMenuStatus_missingOrBlankStatusReturns400() throws Exception {
    Menu menu = createMenu(user, MONDAY, MenuStatus.drafted);

    mockMvc
        .perform(
            post("/api/menus/{menuId}/status", menu.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/menus/{menuId}/status", menu.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"  \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void changeMenuStatus_withoutJwtReturns401() throws Exception {
    mockMvc
        .perform(
            post("/api/menus/{menuId}/status", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusJson("scheduled")))
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

  private String patchMenuJson(List<Map<String, Object>> days, String notes)
      throws JsonProcessingException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("days", days);
    if (notes != null) {
      body.put("notes", notes);
    }
    return objectMapper.writeValueAsString(body);
  }

  private String statusJson(String status) throws JsonProcessingException {
    return objectMapper.writeValueAsString(Map.of("status", status));
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
