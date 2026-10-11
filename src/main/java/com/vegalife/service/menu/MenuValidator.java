package com.vegalife.service.menu;

import com.vegalife.dto.request.menu.CreateMenuRequest;
import com.vegalife.dto.request.menu.MealRequest;
import com.vegalife.dto.request.menu.MenuDayRequest;
import com.vegalife.model.menu.MealType;
import com.vegalife.model.recipe.Dish;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.shared.exception.MenuValidationException;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Validates a create-menu payload against BR-MENU-005 before anything is persisted: the 7-day
 * shape, one meal per meal type per day, servings bounds, dish existence, and the variety
 * thresholds. Thresholds are class constants and deliberately tunable.
 */
@Service
@RequiredArgsConstructor
public class MenuValidator {

  static final int DAYS_PER_WEEK = 7;
  static final int MIN_MEALS_PER_DAY = 1;
  static final int MAX_MEALS_PER_DAY = 3;
  static final int MIN_SERVINGS = 1;
  static final int MAX_DISH_REPEATS = 3;
  static final int MIN_DISTINCT_DISHES = 5;

  private final DishRepository dishRepository;

  /**
   * @throws MenuValidationException when any BR-MENU-005 rule is violated.
   */
  public void validate(CreateMenuRequest request) {
    LocalDate startDate = request.getStartDate();
    if (startDate == null) {
      throw new MenuValidationException("startDate is required");
    }
    List<MenuDayRequest> days = request.getDays();
    if (days == null || days.size() != DAYS_PER_WEEK) {
      throw new MenuValidationException(
          "days must contain exactly the 7 dates from startDate to startDate + 6");
    }

    Set<LocalDate> expectedDates =
        IntStream.range(0, DAYS_PER_WEEK)
            .mapToObj(startDate::plusDays)
            .collect(Collectors.toCollection(HashSet::new));
    Set<LocalDate> seenDates = new HashSet<>();
    for (MenuDayRequest day : days) {
      if (day.getDate() == null) {
        throw new MenuValidationException("each day requires a date");
      }
      if (!expectedDates.contains(day.getDate())) {
        throw new MenuValidationException(
            "day dates must lie within the week starting at startDate");
      }
      if (!seenDates.add(day.getDate())) {
        throw new MenuValidationException("each date of the week may appear at most once");
      }
      validateDay(day);
    }
    validateVariety(days);
  }

  private void validateDay(MenuDayRequest day) {
    List<MealRequest> meals = day.getMeals();
    if (meals == null || meals.size() < MIN_MEALS_PER_DAY || meals.size() > MAX_MEALS_PER_DAY) {
      throw new MenuValidationException("each day must have between 1 and 3 meals");
    }
    Set<MealType> seenTypes = EnumSet.noneOf(MealType.class);
    for (MealRequest meal : meals) {
      MealType mealType = parseMealType(meal.getMealType());
      if (!seenTypes.add(mealType)) {
        throw new MenuValidationException("a date may have at most one meal per mealType");
      }
      if (meal.getDishId() == null) {
        throw new MenuValidationException("each meal requires a dishId");
      }
      if (meal.getServings() == null || meal.getServings() < MIN_SERVINGS) {
        throw new MenuValidationException("servings must be an integer >= 1");
      }
    }
  }

  /** Normalizes and parses a raw meal type; shared with the service for persistence. */
  static MealType parseMealType(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new MenuValidationException("mealType is required");
    }
    try {
      return MealType.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new MenuValidationException(
          "mealType must be one of BREAKFAST, LUNCH, DINNER but was '" + raw + "'");
    }
  }

  private void validateVariety(List<MenuDayRequest> days) {
    List<UUID> dishIds =
        days.stream().flatMap(day -> day.getMeals().stream()).map(MealRequest::getDishId).toList();
    Map<UUID, Long> occurrences =
        dishIds.stream().collect(Collectors.groupingBy(id -> id, Collectors.counting()));

    Set<UUID> existingDishIds =
        dishRepository.findAllById(occurrences.keySet()).stream()
            .filter(dish -> dish.getDeletedAt() == null)
            .map(Dish::getId)
            .collect(Collectors.toSet());
    Optional<UUID> missing =
        occurrences.keySet().stream().filter(id -> !existingDishIds.contains(id)).findFirst();
    if (missing.isPresent()) {
      throw new MenuValidationException("dish not found or deleted: " + missing.get());
    }

    occurrences.values().stream()
        .filter(count -> count > MAX_DISH_REPEATS)
        .findFirst()
        .ifPresent(
            count -> {
              throw new MenuValidationException(
                  "the same dish may appear at most " + MAX_DISH_REPEATS + " times in a menu");
            });
    if (occurrences.size() < MIN_DISTINCT_DISHES) {
      throw new MenuValidationException(
          "a menu must reference at least " + MIN_DISTINCT_DISHES + " distinct dishes");
    }
  }
}
