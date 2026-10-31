package com.vegalife.unit.service.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.response.menu.DishResponse;
import com.vegalife.dto.response.menu.MealDetailResponse;
import com.vegalife.dto.response.menu.MenuMealPlanResponse;
import com.vegalife.dto.response.menu.MenuSummaryResponse;
import com.vegalife.dto.response.menu.RecipeSummaryResponse;
import com.vegalife.enums.menu.MealType;
import com.vegalife.enums.menu.MenuStatus;
import com.vegalife.model.menu.Menu;
import com.vegalife.model.menu.MenuDetail;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.repository.menu.MenuDetailRepository;
import com.vegalife.repository.menu.MenuRepository;
import com.vegalife.service.menu.MenuQueryFilter;
import com.vegalife.service.menu.MenuService;
import com.vegalife.service.recipe.RecipeRecommendationService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class MenuServiceQueryTest {

  private static final UUID USER = UUID.randomUUID();
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
  private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

  @Mock private MenuRepository menuRepository;

  @Mock private MenuDetailRepository menuDetailRepository;

  @Mock private RecipeRecommendationService recipeRecommendationService;

  @InjectMocks private MenuService menuService;

  @Test
  void listMenusPassesWindowBoundsAndStatusToTheQuery() {
    MenuQueryFilter filter =
        MenuQueryFilter.builder().from(MONDAY).to(SUNDAY).status(MenuStatus.scheduled).build();
    Page<Menu> page = new PageImpl<>(List.of(menu(MONDAY, SUNDAY, MenuStatus.scheduled)));
    when(menuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

    PageResponse<MenuSummaryResponse> response = menuService.listMenus(USER, filter, 0, 20, null);

    assertThat(response.getContent()).hasSize(1);
    assertThat(response.getContent().get(0).getStatus()).isEqualTo("scheduled");
  }

  @Test
  void listMenusDefaultsToStartDateDescending() {
    MenuQueryFilter filter = MenuQueryFilter.builder().build();
    when(menuRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    menuService.listMenus(USER, filter, 0, 20, null);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(menuRepository).findAll(any(Specification.class), captor.capture());
    Sort.Order order = captor.getValue().getSort().getOrderFor("startDate");
    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void listMenusUsesTheAuthenticatedUserAsOwner() {
    MenuQueryFilter filter = MenuQueryFilter.builder().from(MONDAY).to(SUNDAY).build();
    when(menuRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    menuService.listMenus(USER, filter, 0, 20, "startDate,asc");

    verify(menuRepository).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  void listMealPlansByMenuIdReturnsDaysGroupedAndMealsOrdered() {
    UUID menuId = UUID.randomUUID();
    Menu menu = menu(MONDAY, SUNDAY, MenuStatus.scheduled);
    menu.setId(menuId);
    Dish dish =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("Tofu scramble")
            .description("Crumbed firm tofu")
            .imageUrl("https://cdn.example.com/tofu.jpg")
            .cuisineType("Asian")
            .build();
    MenuDetail breakfast =
        MenuDetail.builder()
            .menuId(menuId)
            .date(MONDAY)
            .mealType(MealType.BREAKFAST)
            .dish(dish)
            .servings(2)
            .build();
    MenuDetail lunch =
        MenuDetail.builder()
            .menuId(menuId)
            .date(MONDAY)
            .mealType(MealType.LUNCH)
            .dish(dish)
            .servings(1)
            .build();
    when(menuRepository.findByIdAndUserId(menuId, USER)).thenReturn(Optional.of(menu));
    when(menuDetailRepository.findByMenuIdInWithDish(List.of(menuId)))
        .thenReturn(List.of(breakfast, lunch));

    List<MenuMealPlanResponse> response =
        menuService.listMealPlans(USER, MenuQueryFilter.builder().build(), menuId);

    assertThat(response).hasSize(1);
    MenuMealPlanResponse plan = response.get(0);
    assertThat(plan.getId()).isEqualTo(menuId);
    assertThat(plan.getStatus()).isEqualTo("scheduled");
    assertThat(plan.getDays()).hasSize(1);
    assertThat(plan.getDays().get(0).getDate()).isEqualTo(MONDAY);
    assertThat(plan.getDays().get(0).getMeals()).hasSize(2);
    assertThat(plan.getDays().get(0).getMeals().get(0).getMealType()).isEqualTo("BREAKFAST");
    assertThat(plan.getDays().get(0).getMeals().get(0).getServings()).isEqualTo(2);
  }

  @Test
  void listMealPlansEmbedsTheFullDishRatherThanAnId() {
    UUID menuId = UUID.randomUUID();
    Menu menu = menu(MONDAY, SUNDAY, MenuStatus.drafted);
    menu.setId(menuId);
    Dish dish =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("Chickpea salad")
            .description("Chickpeas, cucumber, lemon and dill")
            .cuisineType("Mediterranean")
            .build();
    when(menuRepository.findByIdAndUserId(menuId, USER)).thenReturn(Optional.of(menu));
    when(menuDetailRepository.findByMenuIdInWithDish(List.of(menuId)))
        .thenReturn(
            List.of(
                MenuDetail.builder()
                    .menuId(menuId)
                    .date(MONDAY)
                    .mealType(MealType.LUNCH)
                    .dish(dish)
                    .servings(1)
                    .build()));

    DishResponse embedded =
        menuService
            .listMealPlans(USER, MenuQueryFilter.builder().build(), menuId)
            .get(0)
            .getDays()
            .get(0)
            .getMeals()
            .get(0)
            .getDish();

    assertThat(embedded.getId()).isEqualTo(dish.getId());
    assertThat(embedded.getName()).isEqualTo("Chickpea salad");
    assertThat(embedded.getDescription()).isEqualTo("Chickpeas, cucumber, lemon and dill");
    assertThat(embedded.getCuisineType()).isEqualTo("Mediterranean");
  }

  @Test
  void listMealPlansAttachesRecommendedRecipesToEachMeal() {
    UUID menuId = UUID.randomUUID();
    Menu menu = menu(MONDAY, SUNDAY, MenuStatus.drafted);
    menu.setId(menuId);
    Dish dish = Dish.builder().id(UUID.randomUUID()).name("Chickpea salad").build();
    Recipe recommended = Recipe.builder().id(UUID.randomUUID()).name("Lemon chickpea").build();
    when(menuRepository.findByIdAndUserId(menuId, USER)).thenReturn(Optional.of(menu));
    when(menuDetailRepository.findByMenuIdInWithDish(List.of(menuId)))
        .thenReturn(
            List.of(
                MenuDetail.builder()
                    .menuId(menuId)
                    .date(MONDAY)
                    .mealType(MealType.LUNCH)
                    .dish(dish)
                    .servings(1)
                    .build()));
    when(recipeRecommendationService.recommendFor(any(), any(), any(), anyInt()))
        .thenReturn(Map.of(dish.getId(), List.of(recommended)));

    MealDetailResponse meal =
        menuService
            .listMealPlans(USER, MenuQueryFilter.builder().build(), menuId)
            .get(0)
            .getDays()
            .get(0)
            .getMeals()
            .get(0);

    assertThat(meal.getRecommendedRecipes())
        .extracting(RecipeSummaryResponse::getName)
        .containsExactly("Lemon chickpea");
  }

  @Test
  void listMealPlansKeepsTheMealWhenNoRecipeQualifies() {
    UUID menuId = UUID.randomUUID();
    Menu menu = menu(MONDAY, SUNDAY, MenuStatus.drafted);
    menu.setId(menuId);
    Dish dish = Dish.builder().id(UUID.randomUUID()).name("Pad Thai").build();
    when(menuRepository.findByIdAndUserId(menuId, USER)).thenReturn(Optional.of(menu));
    when(menuDetailRepository.findByMenuIdInWithDish(List.of(menuId)))
        .thenReturn(
            List.of(
                MenuDetail.builder()
                    .menuId(menuId)
                    .date(MONDAY)
                    .mealType(MealType.LUNCH)
                    .dish(dish)
                    .servings(1)
                    .build()));
    when(recipeRecommendationService.recommendFor(any(), any(), any(), anyInt()))
        .thenReturn(Map.of());

    MealDetailResponse meal =
        menuService
            .listMealPlans(USER, MenuQueryFilter.builder().build(), menuId)
            .get(0)
            .getDays()
            .get(0)
            .getMeals()
            .get(0);

    // The meal is part of a saved plan: it stays, with an empty recommendation list.
    assertThat(meal.getRecommendedRecipes()).isEmpty();
  }

  @Test
  void listMealPlansOrdersMenusByStartDateAscending() {
    when(menuRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

    menuService.listMealPlans(
        USER, MenuQueryFilter.builder().from(MONDAY).to(SUNDAY).build(), null);

    ArgumentCaptor<Sort> captor = ArgumentCaptor.forClass(Sort.class);
    verify(menuRepository).findAll(any(Specification.class), captor.capture());
    Sort.Order order = captor.getValue().getOrderFor("startDate");
    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @Test
  void listMealPlansLoadsDetailsOnceForTheWholeWindow() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    Menu earlier = menu(MONDAY, SUNDAY, MenuStatus.scheduled);
    earlier.setId(first);
    Menu later = menu(MONDAY.plusDays(7), SUNDAY.plusDays(7), MenuStatus.drafted);
    later.setId(second);
    when(menuRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of(earlier, later));
    when(menuDetailRepository.findByMenuIdInWithDish(List.of(first, second)))
        .thenReturn(
            List.of(
                MenuDetail.builder()
                    .menuId(second)
                    .date(MONDAY.plusDays(7))
                    .mealType(MealType.DINNER)
                    .dish(Dish.builder().id(UUID.randomUUID()).name("Lentil soup").build())
                    .servings(1)
                    .build()));

    List<MenuMealPlanResponse> response =
        menuService.listMealPlans(
            USER, MenuQueryFilter.builder().from(MONDAY).to(SUNDAY).build(), null);

    assertThat(response).hasSize(2);
    // The second menu has a meal, the first has none — an absent day set must not be null.
    assertThat(response.get(0).getDays()).isEmpty();
    assertThat(response.get(1).getDays()).hasSize(1);
    // Exactly one detail query for the whole window, not one per menu.
    verify(menuDetailRepository, times(1)).findByMenuIdInWithDish(any());
  }

  @Test
  void listMealPlansOfForeignOrMissingIdIsNotFound() {
    UUID menuId = UUID.randomUUID();
    when(menuRepository.findByIdAndUserId(menuId, USER)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> menuService.listMealPlans(USER, MenuQueryFilter.builder().build(), menuId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Menu not found");
  }

  private Menu menu(LocalDate start, LocalDate end, MenuStatus status) {
    return Menu.builder()
        .id(UUID.randomUUID())
        .userId(USER)
        .startDate(start)
        .endDate(end)
        .status(status)
        .build();
  }
}
