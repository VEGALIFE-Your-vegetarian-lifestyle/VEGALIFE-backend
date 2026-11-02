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
import com.vegalife.dto.response.menu.MenuDetailResponse;
import com.vegalife.enums.menu.MealType;
import com.vegalife.enums.menu.MenuStatus;
import com.vegalife.model.menu.Menu;
import com.vegalife.model.menu.MenuDetail;
import com.vegalife.model.recipe.Dish;
import com.vegalife.repository.menu.MenuDetailRepository;
import com.vegalife.repository.menu.MenuRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.service.menu.MenuService;
import com.vegalife.service.menu.MenuValidator;
import com.vegalife.shared.exception.MenuValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
class MenuServiceCreateTest {

  private static final UUID USER = UUID.randomUUID();
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

  @Mock private MenuRepository menuRepository;

  @Mock private MenuDetailRepository menuDetailRepository;

  @Mock private MenuValidator menuValidator;

  @Mock private DishRepository dishRepository;

  @Captor private ArgumentCaptor<List<MenuDetail>> detailsCaptor;

  @InjectMocks private MenuService menuService;

  @Test
  void createMenuPersistsADraftedMenuWithDerivedEndDate() {
    CreateMenuRequest request = validRequest();
    when(menuRepository.save(any(Menu.class)))
        .thenAnswer(
            invocation -> {
              Menu menu = invocation.getArgument(0);
              menu.setId(UUID.randomUUID());
              return menu;
            });
    when(dishRepository.findAllById(any())).thenReturn(dishesOf(request));
    when(menuDetailRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    MenuDetailResponse response = menuService.createMenu(USER, request);

    ArgumentCaptor<Menu> menuCaptor = ArgumentCaptor.forClass(Menu.class);
    verify(menuRepository).save(menuCaptor.capture());
    Menu saved = menuCaptor.getValue();
    assertThat(saved.getUserId()).isEqualTo(USER);
    assertThat(saved.getStartDate()).isEqualTo(MONDAY);
    assertThat(saved.getEndDate()).isEqualTo(MONDAY.plusDays(6));
    assertThat(saved.getStatus()).isEqualTo(MenuStatus.drafted);

    assertThat(response.getId()).isEqualTo(saved.getId());
    assertThat(response.getStatus()).isEqualTo("drafted");
    assertThat(response.getStartDate()).isEqualTo(MONDAY);
    assertThat(response.getEndDate()).isEqualTo(MONDAY.plusDays(6));
    assertThat(response.getDays()).hasSize(7);
    assertThat(response.getDays().get(0).getMeals()).hasSize(1);
    assertThat(response.getDays().get(0).getMeals().get(0).getServings()).isEqualTo(2);
  }

  @Test
  void createMenuPersistsOneDetailRowPerMealInDateOrder() {
    CreateMenuRequest request = validRequest();
    when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(dishRepository.findAllById(any())).thenReturn(dishesOf(request));
    when(menuDetailRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    menuService.createMenu(USER, request);

    verify(menuDetailRepository).saveAll(detailsCaptor.capture());
    List<MenuDetail> savedDetails = detailsCaptor.getValue();
    assertThat(savedDetails).hasSize(7);
    assertThat(savedDetails)
        .isSortedAccordingTo(
            Comparator.comparing(MenuDetail::getDate).thenComparing(MenuDetail::getMealType));
    assertThat(savedDetails.get(0).getMealType()).isEqualTo(MealType.BREAKFAST);
    assertThat(savedDetails.get(0).getDish().getId()).isNotNull();
  }

  @Test
  void createMenuPersistsNothingWhenValidationFails() {
    CreateMenuRequest request = validRequest();
    doThrowValidation();

    assertThatThrownBy(() -> menuService.createMenu(USER, request))
        .isInstanceOf(MenuValidationException.class);

    verify(menuRepository, never()).save(any(Menu.class));
    verify(menuDetailRepository, never()).saveAll(any());
  }

  private void doThrowValidation() {
    doThrow(new MenuValidationException("days must contain exactly the 7 dates"))
        .when(menuValidator)
        .validate(any(CreateMenuRequest.class));
  }

  private CreateMenuRequest validRequest() {
    List<MenuDayRequest> days =
        IntStream.range(0, 7)
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
    return CreateMenuRequest.builder().startDate(MONDAY).days(new ArrayList<>(days)).build();
  }

  private List<Dish> dishesOf(CreateMenuRequest request) {
    return request.getDays().stream()
        .flatMap(day -> day.getMeals().stream())
        .map(MealRequest::getDishId)
        .distinct()
        .map(id -> Dish.builder().id(id).name("Dish " + id).build())
        .toList();
  }
}
