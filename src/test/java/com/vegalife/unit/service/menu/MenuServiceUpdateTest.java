package com.vegalife.unit.service.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.menu.CreateMenuRequest;
import com.vegalife.dto.request.menu.MealRequest;
import com.vegalife.dto.request.menu.MenuDayRequest;
import com.vegalife.dto.request.menu.UpdateMenuRequest;
import com.vegalife.dto.response.menu.MenuDetailResponse;
import com.vegalife.enums.menu.MenuStatus;
import com.vegalife.model.menu.Menu;
import com.vegalife.model.recipe.Dish;
import com.vegalife.repository.menu.MenuDetailRepository;
import com.vegalife.repository.menu.MenuRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.service.menu.MenuService;
import com.vegalife.service.menu.MenuValidator;
import com.vegalife.shared.exception.MenuNotEditableException;
import com.vegalife.shared.exception.MenuValidationException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuServiceUpdateTest {

  private static final UUID USER = UUID.randomUUID();
  private static final UUID MENU_ID = UUID.randomUUID();
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

  @Mock private MenuRepository menuRepository;

  @Mock private MenuDetailRepository menuDetailRepository;

  @Mock private MenuValidator menuValidator;

  @Mock private DishRepository dishRepository;

  @Captor private ArgumentCaptor<CreateMenuRequest> revalidationCaptor;

  @InjectMocks private MenuService menuService;

  @Test
  void updateMenuReplacesDaysAndUpdatesFreeTextFieldsOnADraft() {
    List<MenuDayRequest> week = newWeek();
    when(menuRepository.findByIdAndUserId(MENU_ID, USER))
        .thenReturn(Optional.of(menu(MenuStatus.drafted)));
    when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(dishRepository.findAllById(any())).thenReturn(dishesOf(week));
    when(menuDetailRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    UpdateMenuRequest request = UpdateMenuRequest.builder().days(week).notes("new").build();

    MenuDetailResponse response = menuService.updateMenu(USER, MENU_ID, request);

    verify(menuValidator).validate(revalidationCaptor.capture());
    assertThat(revalidationCaptor.getValue().getStartDate()).isEqualTo(MONDAY);
    assertThat(revalidationCaptor.getValue().getDays()).hasSize(7);

    ArgumentCaptor<Menu> menuCaptor = ArgumentCaptor.forClass(Menu.class);
    verify(menuRepository).save(menuCaptor.capture());
    assertThat(menuCaptor.getValue().getNotes()).isEqualTo("new");
    assertThat(menuCaptor.getValue().getStartDate()).isEqualTo(MONDAY);
    assertThat(menuCaptor.getValue().getEndDate()).isEqualTo(MONDAY.plusDays(6));

    verify(menuDetailRepository).deleteByMenuId(MENU_ID);
    assertThat(response.getStatus()).isEqualTo("drafted");
    assertThat(response.getDays()).hasSize(7);
  }

  @Test
  void updateMenuAllowsEditingAScheduledMenu() {
    List<MenuDayRequest> week = newWeek();
    when(menuRepository.findByIdAndUserId(MENU_ID, USER))
        .thenReturn(Optional.of(menu(MenuStatus.scheduled)));
    when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(dishRepository.findAllById(any())).thenReturn(dishesOf(week));
    when(menuDetailRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    MenuDetailResponse response =
        menuService.updateMenu(USER, MENU_ID, UpdateMenuRequest.builder().days(week).build());

    assertThat(response.getStatus()).isEqualTo("scheduled");
    verify(menuDetailRepository).deleteByMenuId(MENU_ID);
  }

  @Test
  void updateMenuKeepsFreeTextFieldsWhenOmitted() {
    List<MenuDayRequest> week = newWeek();
    Menu existing = menu(MenuStatus.drafted);
    existing.setNotes("original");
    when(menuRepository.findByIdAndUserId(MENU_ID, USER)).thenReturn(Optional.of(existing));
    when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(dishRepository.findAllById(any())).thenReturn(dishesOf(week));
    when(menuDetailRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    menuService.updateMenu(USER, MENU_ID, UpdateMenuRequest.builder().days(week).build());

    verify(menuValidator).validate(revalidationCaptor.capture());
    assertThat(revalidationCaptor.getValue().getNotes()).isEqualTo("original");
  }

  @Test
  void updateMenuRejectsCancelledAndCompletedMenus() {
    for (MenuStatus status : List.of(MenuStatus.cancelled, MenuStatus.completed)) {
      when(menuRepository.findByIdAndUserId(MENU_ID, USER)).thenReturn(Optional.of(menu(status)));

      assertThatThrownBy(
              () ->
                  menuService.updateMenu(
                      USER, MENU_ID, UpdateMenuRequest.builder().days(newWeek()).build()))
          .isInstanceOf(MenuNotEditableException.class)
          .hasMessageContaining("Only drafted or scheduled menus can be updated");
    }
    verify(menuDetailRepository, never()).deleteByMenuId(any());
    verify(menuRepository, never()).save(any(Menu.class));
  }

  @Test
  void updateMenuForeignOrUnknownIdIsNotFound() {
    when(menuRepository.findByIdAndUserId(MENU_ID, USER)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                menuService.updateMenu(
                    USER, MENU_ID, UpdateMenuRequest.builder().days(newWeek()).build()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Menu not found");
    verify(menuDetailRepository, never()).deleteByMenuId(any());
  }

  @Test
  void updateMenuPersistsNothingWhenRevalidationFails() {
    when(menuRepository.findByIdAndUserId(MENU_ID, USER))
        .thenReturn(Optional.of(menu(MenuStatus.drafted)));
    doThrow(new MenuValidationException("a menu must reference at least 5 distinct dishes"))
        .when(menuValidator)
        .validate(any(CreateMenuRequest.class));

    assertThatThrownBy(
            () ->
                menuService.updateMenu(
                    USER, MENU_ID, UpdateMenuRequest.builder().days(newWeek()).build()))
        .isInstanceOf(MenuValidationException.class);

    verify(menuDetailRepository, never()).deleteByMenuId(any());
    verify(menuRepository, never()).save(any(Menu.class));
  }

  private Menu menu(MenuStatus status) {
    return Menu.builder()
        .id(MENU_ID)
        .userId(USER)
        .startDate(MONDAY)
        .endDate(MONDAY.plusDays(6))
        .status(status)
        .build();
  }

  private List<MenuDayRequest> newWeek() {
    return IntStream.range(0, 7)
        .mapToObj(
            offset ->
                MenuDayRequest.builder()
                    .date(MONDAY.plusDays(offset))
                    .meals(
                        new ArrayList<>(
                            List.of(
                                MealRequest.builder()
                                    .mealType("BREAKFAST")
                                    .dishId(UUID.randomUUID())
                                    .servings(2)
                                    .build())))
                    .build())
        .toList();
  }

  private List<Dish> dishesOf(List<MenuDayRequest> days) {
    return days.stream()
        .flatMap(day -> day.getMeals().stream())
        .map(MealRequest::getDishId)
        .distinct()
        .map(id -> Dish.builder().id(id).name("Dish " + id).build())
        .toList();
  }
}
