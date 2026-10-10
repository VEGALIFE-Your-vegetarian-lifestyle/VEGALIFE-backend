package com.vegalife.unit.service.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.response.menu.MenuDetailResponse;
import com.vegalife.dto.response.menu.MenuSummaryResponse;
import com.vegalife.model.menu.MealType;
import com.vegalife.model.menu.Menu;
import com.vegalife.model.menu.MenuDetail;
import com.vegalife.model.menu.MenuStatus;
import com.vegalife.model.recipe.Dish;
import com.vegalife.repository.menu.MenuDetailRepository;
import com.vegalife.repository.menu.MenuRepository;
import com.vegalife.service.menu.MenuQueryFilter;
import com.vegalife.service.menu.MenuService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
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

@ExtendWith(MockitoExtension.class)
class MenuServiceQueryTest {

  private static final UUID USER = UUID.randomUUID();
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
  private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

  @Mock private MenuRepository menuRepository;

  @Mock private MenuDetailRepository menuDetailRepository;

  @InjectMocks private MenuService menuService;

  @Test
  void listMenusPassesWindowBoundsAndStatusToTheQuery() {
    MenuQueryFilter filter =
        MenuQueryFilter.builder().from(MONDAY).to(SUNDAY).status(MenuStatus.scheduled).build();
    Page<Menu> page = new PageImpl<>(List.of(menu(MONDAY, SUNDAY, MenuStatus.scheduled)));
    when(menuRepository.findOwnMenus(
            eq(USER), eq(MONDAY), eq(SUNDAY), eq(MenuStatus.scheduled), any(Pageable.class)))
        .thenReturn(page);

    PageResponse<MenuSummaryResponse> response = menuService.listMenus(USER, filter, 0, 20, null);

    assertThat(response.getContent()).hasSize(1);
    assertThat(response.getContent().get(0).getStatus()).isEqualTo("scheduled");
  }

  @Test
  void listMenusDefaultsToStartDateDescending() {
    MenuQueryFilter filter = MenuQueryFilter.builder().build();
    when(menuRepository.findOwnMenus(eq(USER), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    menuService.listMenus(USER, filter, 0, 20, null);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(menuRepository).findOwnMenus(eq(USER), isNull(), isNull(), isNull(), captor.capture());
    Sort.Order order = captor.getValue().getSort().getOrderFor("startDate");
    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void listMenusUsesTheAuthenticatedUserAsOwner() {
    MenuQueryFilter filter = MenuQueryFilter.builder().from(MONDAY).to(SUNDAY).build();
    when(menuRepository.findOwnMenus(any(), any(), any(), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    menuService.listMenus(USER, filter, 0, 20, "startDate,asc");

    verify(menuRepository)
        .findOwnMenus(eq(USER), eq(MONDAY), eq(SUNDAY), isNull(), any(Pageable.class));
  }

  @Test
  void getMenuReturnsDaysGroupedAndMealsOrdered() {
    UUID menuId = UUID.randomUUID();
    Menu menu = menu(MONDAY, SUNDAY, MenuStatus.scheduled);
    menu.setId(menuId);
    Dish dish = Dish.builder().id(UUID.randomUUID()).name("Tofu scramble").build();
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
    when(menuDetailRepository.findByMenuIdOrderByDateAscMealTypeAsc(menuId))
        .thenReturn(List.of(breakfast, lunch));

    MenuDetailResponse response = menuService.getMenu(USER, menuId);

    assertThat(response.getDays()).hasSize(1);
    assertThat(response.getDays().get(0).getDate()).isEqualTo(MONDAY);
    assertThat(response.getDays().get(0).getMeals()).hasSize(2);
    assertThat(response.getDays().get(0).getMeals().get(0).getMealType()).isEqualTo("BREAKFAST");
    assertThat(response.getDays().get(0).getMeals().get(0).getDishName())
        .isEqualTo("Tofu scramble");
    assertThat(response.getDays().get(0).getMeals().get(0).getServings()).isEqualTo(2);
  }

  @Test
  void getMenuOfForeignOrMissingIdIsNotFound() {
    UUID menuId = UUID.randomUUID();
    when(menuRepository.findByIdAndUserId(menuId, USER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> menuService.getMenu(USER, menuId))
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
