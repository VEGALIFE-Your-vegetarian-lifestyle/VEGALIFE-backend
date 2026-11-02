package com.vegalife.unit.service.menu;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.menu.CreateMenuRequest;
import com.vegalife.dto.request.menu.MealRequest;
import com.vegalife.dto.request.menu.MenuDayRequest;
import com.vegalife.model.recipe.Dish;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.service.menu.MenuValidator;
import com.vegalife.shared.exception.MenuValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuValidatorTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

  @Mock private DishRepository dishRepository;

  @InjectMocks private MenuValidator menuValidator;

  @Test
  void acceptsAWellFormedSevenDayMenu() {
    List<MenuDayRequest> days = daysWithOneDistinctMealPerDay();
    mockExistingDishes(days);
    CreateMenuRequest request = request(days);

    assertThatCode(() -> menuValidator.validate(request)).doesNotThrowAnyException();
  }

  @Test
  void acceptsMealTypesCaseInsensitively() {
    List<MenuDayRequest> days = daysWithOneDistinctMealPerDay();
    days.get(0).getMeals().get(0).setMealType("breakfast");
    mockExistingDishes(days);

    assertThatCode(() -> menuValidator.validate(request(days))).doesNotThrowAnyException();
  }

  @Test
  void rejectsANonSevenDayList() {
    List<MenuDayRequest> days = daysWithOneDistinctMealPerDay().subList(0, 6);

    assertThatThrownBy(() -> menuValidator.validate(request(days)))
        .isInstanceOf(MenuValidationException.class)
        .hasMessageContaining("exactly the 7 dates");
  }

  @Test
  void rejectsADateOutsideTheWeek() {
    List<MenuDayRequest> days = daysWithOneDistinctMealPerDay();
    days.get(6).setDate(MONDAY.plusDays(7));

    assertThatThrownBy(() -> menuValidator.validate(request(days)))
        .isInstanceOf(MenuValidationException.class)
        .hasMessageContaining("within the week");
  }

  @Test
  void rejectsADuplicateDate() {
    List<MenuDayRequest> days = daysWithOneDistinctMealPerDay();
    days.get(6).setDate(days.get(5).getDate());

    assertThatThrownBy(() -> menuValidator.validate(request(days)))
        .isInstanceOf(MenuValidationException.class)
        .hasMessageContaining("at most once");
  }

  @Test
  void rejectsAnUnknownMealType() {
    List<MenuDayRequest> days = daysWithOneDistinctMealPerDay();
    days.get(0).getMeals().get(0).setMealType("SNACK");

    assertThatThrownBy(() -> menuValidator.validate(request(days)))
        .isInstanceOf(MenuValidationException.class)
        .hasMessageContaining("BREAKFAST, LUNCH, DINNER");
  }

  @Test
  void rejectsTwoMealsOfTheSameTypeOnOneDay() {
    List<MenuDayRequest> days = daysWithOneDistinctMealPerDay();
    days.get(0).getMeals().add(meal("BREAKFAST", UUID.randomUUID()));

    assertThatThrownBy(() -> menuValidator.validate(request(days)))
        .isInstanceOf(MenuValidationException.class)
        .hasMessageContaining("one meal per mealType");
  }

  @Test
  void rejectsZeroServings() {
    List<MenuDayRequest> days = daysWithOneDistinctMealPerDay();
    days.get(0).getMeals().get(0).setServings(0);

    assertThatThrownBy(() -> menuValidator.validate(request(days)))
        .isInstanceOf(MenuValidationException.class)
        .hasMessageContaining("servings");
  }

  @Test
  void rejectsAMissingDish() {
    List<MenuDayRequest> days = daysWithOneDistinctMealPerDay();
    UUID missingDish = days.get(0).getMeals().get(0).getDishId();
    when(dishRepository.findAllById(any())).thenReturn(dishesExcept(days, missingDish));

    assertThatThrownBy(() -> menuValidator.validate(request(days)))
        .isInstanceOf(MenuValidationException.class)
        .hasMessageContaining("dish not found or deleted");
  }

  @Test
  void rejectsADishRepeatedTooOften() {
    List<MenuDayRequest> days = daysWithOneDistinctMealPerDay();
    UUID favourite = days.get(0).getMeals().get(0).getDishId();
    for (int i = 1; i <= 3; i++) {
      days.get(i).getMeals().get(0).setDishId(favourite);
    }
    mockExistingDishes(days);

    assertThatThrownBy(() -> menuValidator.validate(request(days)))
        .isInstanceOf(MenuValidationException.class)
        .hasMessageContaining("at most 3 times");
  }

  @Test
  void rejectsTooFewDistinctDishes() {
    List<MenuDayRequest> days = daysWithOneDistinctMealPerDay();
    List<UUID> fourDishes =
        List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    for (int i = 0; i < days.size(); i++) {
      days.get(i).getMeals().get(0).setDishId(fourDishes.get(i % fourDishes.size()));
    }
    mockExistingDishes(days);

    assertThatThrownBy(() -> menuValidator.validate(request(days)))
        .isInstanceOf(MenuValidationException.class)
        .hasMessageContaining("at least 5 distinct dishes");
  }

  /** Seven days, one meal each, seven distinct dishes — the minimal valid menu. */
  private List<MenuDayRequest> daysWithOneDistinctMealPerDay() {
    return IntStream.range(0, 7)
        .mapToObj(
            offset ->
                MenuDayRequest.builder()
                    .date(MONDAY.plusDays(offset))
                    .meals(new ArrayList<>(List.of(meal("BREAKFAST", UUID.randomUUID()))))
                    .build())
        .toList();
  }

  private MealRequest meal(String mealType, UUID dishId) {
    return MealRequest.builder().mealType(mealType).dishId(dishId).servings(2).build();
  }

  private CreateMenuRequest request(List<MenuDayRequest> days) {
    return CreateMenuRequest.builder().startDate(MONDAY).days(new ArrayList<>(days)).build();
  }

  private void mockExistingDishes(List<MenuDayRequest> days) {
    when(dishRepository.findAllById(any())).thenReturn(dishesExcept(days, null));
  }

  private List<Dish> dishesExcept(List<MenuDayRequest> days, UUID excluded) {
    return days.stream()
        .flatMap(day -> day.getMeals().stream())
        .map(MealRequest::getDishId)
        .filter(id -> !id.equals(excluded))
        .distinct()
        .map(id -> Dish.builder().id(id).name("Dish " + id).build())
        .toList();
  }
}
