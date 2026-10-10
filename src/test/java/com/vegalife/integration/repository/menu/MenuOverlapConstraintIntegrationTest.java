package com.vegalife.integration.repository.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the V30 schema effects on a real Postgres: the btree_gist exclusion constraint rejects
 * overlapping scheduled weeks per user (BR-MENU-003, ADR-010) while leaving drafts and completed
 * rows alone, and the servings column is stored.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class MenuOverlapConstraintIntegrationTest {

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

  @Autowired private UserRepository userRepository;

  @Autowired private MenuRepository menuRepository;

  @Autowired private MenuDetailRepository menuDetailRepository;

  @Autowired private DishRepository dishRepository;

  private User user;
  private User otherUser;

  @BeforeEach
  void setUp() {
    user = createUser("overlapowner", "overlapowner@example.com");
    otherUser = createUser("overlapother", "overlapother@example.com");
  }

  @Test
  void overlappingScheduledWeeksForOneUserAreRejected() {
    menuRepository.saveAndFlush(scheduled(user, LocalDate.of(2026, 10, 5)));

    assertThatThrownBy(
            () ->
                menuRepository.saveAndFlush(
                    Menu.builder()
                        .userId(user.getId())
                        .startDate(LocalDate.of(2026, 10, 8))
                        .endDate(LocalDate.of(2026, 10, 14))
                        .status(MenuStatus.scheduled)
                        .build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void touchingScheduledWeeksAreAllowed() {
    menuRepository.saveAndFlush(scheduled(user, LocalDate.of(2026, 10, 5)));
    menuRepository.saveAndFlush(scheduled(user, LocalDate.of(2026, 10, 12)));

    assertThat(menuRepository.findAll()).hasSize(2);
  }

  @Test
  void sameWeekForDifferentUsersIsAllowed() {
    menuRepository.saveAndFlush(scheduled(user, LocalDate.of(2026, 10, 5)));
    menuRepository.saveAndFlush(scheduled(otherUser, LocalDate.of(2026, 10, 5)));

    assertThat(menuRepository.findAll()).hasSize(2);
  }

  @Test
  void draftAndCompletedOverlapsAreNotConstrained() {
    menuRepository.saveAndFlush(
        Menu.builder()
            .userId(user.getId())
            .startDate(LocalDate.of(2026, 10, 5))
            .endDate(LocalDate.of(2026, 10, 11))
            .status(MenuStatus.drafted)
            .build());
    menuRepository.saveAndFlush(
        Menu.builder()
            .userId(user.getId())
            .startDate(LocalDate.of(2026, 10, 6))
            .endDate(LocalDate.of(2026, 10, 12))
            .status(MenuStatus.completed)
            .build());

    assertThat(menuRepository.findAll()).hasSize(2);
  }

  @Test
  void menuDetailStoresServings() {
    Menu menu = menuRepository.saveAndFlush(scheduled(user, LocalDate.of(2026, 10, 5)));
    Dish dish = dishRepository.saveAndFlush(Dish.builder().name("Dal").build());

    MenuDetail detail =
        menuDetailRepository.saveAndFlush(
            MenuDetail.builder()
                .menuId(menu.getId())
                .dish(dish)
                .date(LocalDate.of(2026, 10, 5))
                .mealType(MealType.DINNER)
                .servings(3)
                .build());

    assertThat(detail.getServings()).isEqualTo(3);
    assertThat(menuDetailRepository.findById(detail.getId())).isPresent();
  }

  private Menu scheduled(User owner, LocalDate start) {
    return Menu.builder()
        .userId(owner.getId())
        .startDate(start)
        .endDate(start.plusDays(6))
        .status(MenuStatus.scheduled)
        .build();
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
}
