package com.vegalife.integration.controller.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.UUID;
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
