package com.vegalife.service.menu;

import com.vegalife.dto.request.menu.CreateMenuRequest;
import com.vegalife.dto.request.menu.MealRequest;
import com.vegalife.dto.request.menu.MenuDayRequest;
import com.vegalife.dto.request.menu.UpdateMenuRequest;
import com.vegalife.dto.response.menu.DishResponse;
import com.vegalife.dto.response.menu.MealDetailResponse;
import com.vegalife.dto.response.menu.MealResponse;
import com.vegalife.dto.response.menu.MenuDayResponse;
import com.vegalife.dto.response.menu.MenuDetailResponse;
import com.vegalife.dto.response.menu.MenuMealPlanDayResponse;
import com.vegalife.dto.response.menu.MenuMealPlanResponse;
import com.vegalife.dto.response.menu.MenuSummaryResponse;
import com.vegalife.enums.menu.MenuStatus;
import com.vegalife.model.menu.Menu;
import com.vegalife.model.menu.MenuDetail;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.repository.menu.MenuDetailRepository;
import com.vegalife.repository.menu.MenuRepository;
import com.vegalife.repository.menu.MenuSpecifications;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.service.recipe.RecipeRecommendationService;
import com.vegalife.service.recipe.RecipeSummaryMapper;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.InvalidMenuTransitionException;
import com.vegalife.shared.exception.MenuNotEditableException;
import com.vegalife.shared.exception.MenuOverlapException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menu reads (windows and detail) plus the write paths — create, update, and status transitions —
 * always scoped to the authenticated owner.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

  private static final String DEFAULT_SORT_PROPERTY = "startDate";

  private final MenuRepository menuRepository;
  private final MenuDetailRepository menuDetailRepository;
  private final MenuValidator menuValidator;
  private final DishRepository dishRepository;
  private final RecipeRecommendationService recipeRecommendationService;

  /**
   * BR-MENU-005: validates the week payload and persists a new {@code drafted} menu with its days
   * in one transaction. The owner is the authenticated caller; {@code endDate} is derived.
   */
  @Transactional
  public MenuDetailResponse createMenu(UUID userId, CreateMenuRequest request) {
    menuValidator.validate(request);

    LocalDate startDate = request.getStartDate();
    Menu menu =
        menuRepository.save(
            Menu.builder()
                .userId(userId)
                .startDate(startDate)
                .endDate(startDate.plusDays(MenuValidator.DAYS_PER_WEEK - 1))
                .status(MenuStatus.drafted)
                .notes(request.getNotes())
                .build());

    List<MenuDetail> details = persistDays(menu.getId(), request.getDays());
    log.info(
        "Created drafted menu {} for user {} with {} meals", menu.getId(), userId, details.size());
    return toDetail(menu, details);
  }

  /**
   * BR-MENU-006: replaces the meals of an owned {@code drafted}/{@code scheduled} menu and updates
   * its free-text fields, re-validating the whole resulting week (BR-MENU-005). The window is
   * immutable, so a scheduled menu cannot gain an overlap through an update — V30's exclusion
   * constraint stays the database backstop.
   */
  @Transactional
  public MenuDetailResponse updateMenu(UUID userId, UUID menuId, UpdateMenuRequest request) {
    Menu menu =
        menuRepository
            .findByIdAndUserId(menuId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Menu not found"));
    if (menu.getStatus() == MenuStatus.cancelled || menu.getStatus() == MenuStatus.completed) {
      throw new MenuNotEditableException("Only drafted or scheduled menus can be updated");
    }

    // The same validator as create, run against the existing window: same rules, same messages.
    menuValidator.validate(
        CreateMenuRequest.builder()
            .startDate(menu.getStartDate())
            .notes(request.getNotes() != null ? request.getNotes() : menu.getNotes())
            .days(request.getDays())
            .build());

    if (request.getNotes() != null) {
      menu.setNotes(request.getNotes());
    }
    menuRepository.save(menu);
    menuDetailRepository.deleteByMenuId(menuId);
    // Hibernate flushes INSERTs before DELETEs otherwise; the unique (menu_id, date, meal_type)
    // constraint would see the old rows still present.
    menuDetailRepository.flush();
    List<MenuDetail> details = persistDays(menuId, request.getDays());
    log.info(
        "Updated {} menu {} for user {} with {} meals",
        menu.getStatus(),
        menuId,
        userId,
        details.size());
    return toDetail(menu, details);
  }

  /**
   * BR-MENU-001 / BR-MENU-002: the caller's own menus intersecting the filter window, optionally by
   * status, newest start date first. The owner comes from the JWT, never a request parameter.
   */
  @Transactional(readOnly = true)
  public PageResponse<MenuSummaryResponse> listMenus(
      UUID userId, MenuQueryFilter filter, int page, int size, String sort) {
    Pageable pageable = PageRequest.of(page, size, parseSort(sort));
    Page<Menu> menus =
        menuRepository.findAll(
            MenuSpecifications.ownedByWithinWindowAndStatus(
                userId, filter.getFrom(), filter.getTo(), filter.getStatus()),
            pageable);
    return PageResponse.from(menus.map(this::toSummary));
  }

  /**
   * BR-MENU-001 / BR-MENU-002: the caller's meal plan over the filter window — every intersecting
   * menu with its days, meals and dishes, ordered by start date. A {@code menuId} selects exactly
   * one menu and overrides the window entirely, so a caller holding an id never has to work out
   * which range would contain it; a missing or foreign id is a 404, never a 403.
   *
   * <p>Each meal also carries up to three recommended recipes (issue #163), filtered by the
   * caller's stored allergies. A meal whose dish has no qualifying recipe keeps an empty list — the
   * meal itself is never removed, since it is part of a plan the caller already saved.
   */
  @Transactional(readOnly = true)
  public List<MenuMealPlanResponse> listMealPlans(
      UUID userId, MenuQueryFilter filter, UUID menuId) {
    if (menuId != null) {
      Menu menu =
          menuRepository
              .findByIdAndUserId(menuId, userId)
              .orElseThrow(() -> new ResourceNotFoundException("Menu not found"));
      return List.of(toMealPlan(menu, detailsForMenus(List.of(menu)), userId));
    }

    List<Menu> menus =
        menuRepository.findAll(
            MenuSpecifications.ownedByWithinWindowAndStatus(
                userId, filter.getFrom(), filter.getTo(), null),
            Sort.by(Sort.Direction.ASC, "startDate"));
    if (menus.isEmpty()) {
      return List.of();
    }

    Map<UUID, List<MenuDetail>> byMenuId =
        detailsForMenus(menus).stream()
            .collect(
                Collectors.groupingBy(
                    MenuDetail::getMenuId, LinkedHashMap::new, Collectors.toList()));
    return menus.stream()
        .map(menu -> toMealPlan(menu, byMenuId.getOrDefault(menu.getId(), List.of()), userId))
        .toList();
  }

  /**
   * Loads the days of the given menus with each dish already fetched, in one query. An empty set
   * short-circuits because {@code IN ()} is not valid JPQL.
   */
  private List<MenuDetail> detailsForMenus(List<Menu> menus) {
    if (menus.isEmpty()) {
      return List.of();
    }
    return menuDetailRepository.findByMenuIdInWithDish(menus.stream().map(Menu::getId).toList());
  }

  /**
   * BR-MENU-007: transitions an owned menu through the legal table — {@code drafted} to {@code
   * scheduled}/{@code cancelled}, {@code scheduled} to {@code cancelled}/{@code completed}, both
   * terminal states final. Scheduling first pre-checks the overlap rule (BR-MENU-003); a violation
   * changes nothing. The target vocabulary excludes {@code drafted}.
   */
  @Transactional
  public MenuDetailResponse changeStatus(UUID userId, UUID menuId, String targetStatus) {
    MenuStatus target = parseTargetStatus(targetStatus);
    Menu menu =
        menuRepository
            .findByIdAndUserId(menuId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Menu not found"));
    if (!isLegalTransition(menu.getStatus(), target)) {
      throw new InvalidMenuTransitionException(
          "Cannot transition a " + menu.getStatus() + " menu to " + target);
    }
    if (target == MenuStatus.scheduled
        && menuRepository
            .existsByUserIdAndStatusAndIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                userId, MenuStatus.scheduled, menuId, menu.getStartDate(), menu.getEndDate())) {
      throw new MenuOverlapException("Another scheduled menu already overlaps this week");
    }

    MenuStatus previous = menu.getStatus();
    menu.setStatus(target);
    menuRepository.save(menu);
    log.info("Changed menu {} for user {} from {} to {}", menuId, userId, previous, target);

    List<MenuDetail> details = menuDetailRepository.findByMenuIdOrderByDateAscMealTypeAsc(menuId);
    return toDetail(menu, details);
  }

  /**
   * BR-MENU-007's transition table: {@code drafted} may be scheduled or cancelled, {@code
   * scheduled} may be cancelled or completed, and the two terminal states allow nothing (including
   * no-ops).
   */
  private boolean isLegalTransition(MenuStatus from, MenuStatus to) {
    return switch (from) {
      case drafted -> to == MenuStatus.scheduled || to == MenuStatus.cancelled;
      case scheduled -> to == MenuStatus.cancelled || to == MenuStatus.completed;
      case cancelled, completed -> false;
    };
  }

  /**
   * BR-MENU-007: the target must be exactly {@code scheduled}, {@code cancelled}, or {@code
   * completed}. {@code drafted} is never a target and anything else is a 400.
   */
  private MenuStatus parseTargetStatus(String raw) {
    if (raw == null) {
      throw new ValidationException("status must be one of scheduled, cancelled, completed");
    }
    return switch (raw.trim()) {
      case "scheduled" -> MenuStatus.scheduled;
      case "cancelled" -> MenuStatus.cancelled;
      case "completed" -> MenuStatus.completed;
      default ->
          throw new ValidationException("status must be one of scheduled, cancelled, completed");
    };
  }

  /**
   * Materialises the validated days into {@code menu_detail} rows, ordered by date then meal type
   * so the persisted order matches the read order.
   */
  private List<MenuDetail> persistDays(UUID menuId, List<MenuDayRequest> days) {
    List<UUID> dishIds =
        days.stream()
            .flatMap(day -> day.getMeals().stream())
            .map(MealRequest::getDishId)
            .distinct()
            .toList();
    Map<UUID, Dish> dishes =
        dishRepository.findAllById(dishIds).stream()
            .collect(Collectors.toMap(Dish::getId, Function.identity()));

    List<MenuDetail> details = new ArrayList<>();
    for (MenuDayRequest day : days) {
      for (MealRequest meal : day.getMeals()) {
        details.add(
            MenuDetail.builder()
                .menuId(menuId)
                .dish(dishes.get(meal.getDishId()))
                .date(day.getDate())
                .mealType(MenuValidator.parseMealType(meal.getMealType()))
                .servings(meal.getServings())
                .build());
      }
    }
    details.sort(Comparator.comparing(MenuDetail::getDate).thenComparing(MenuDetail::getMealType));
    return menuDetailRepository.saveAll(details);
  }

  private MenuSummaryResponse toSummary(Menu menu) {
    return MenuSummaryResponse.builder()
        .id(menu.getId())
        .startDate(menu.getStartDate())
        .endDate(menu.getEndDate())
        .status(menu.getStatus().name())
        .notes(menu.getNotes())
        .createdAt(menu.getCreatedAt())
        .updatedAt(menu.getUpdatedAt())
        .build();
  }

  private MenuDetailResponse toDetail(Menu menu, List<MenuDetail> details) {
    Map<LocalDate, List<MenuDetail>> byDate = new LinkedHashMap<>();
    for (MenuDetail detail : details) {
      byDate.computeIfAbsent(detail.getDate(), key -> new ArrayList<>()).add(detail);
    }
    List<MenuDayResponse> days =
        byDate.entrySet().stream()
            .map(
                entry ->
                    MenuDayResponse.builder()
                        .date(entry.getKey())
                        .meals(entry.getValue().stream().map(this::toMeal).toList())
                        .build())
            .toList();

    return MenuDetailResponse.builder()
        .id(menu.getId())
        .startDate(menu.getStartDate())
        .endDate(menu.getEndDate())
        .status(menu.getStatus().name())
        .notes(menu.getNotes())
        .createdAt(menu.getCreatedAt())
        .updatedAt(menu.getUpdatedAt())
        .days(days)
        .build();
  }

  private MealResponse toMeal(MenuDetail detail) {
    return MealResponse.builder()
        .mealType(detail.getMealType().name())
        .dishId(detail.getDish().getId())
        .dishName(detail.getDish().getName())
        .servings(detail.getServings())
        .build();
  }

  private MenuMealPlanResponse toMealPlan(Menu menu, List<MenuDetail> details, UUID userId) {
    Map<UUID, List<Recipe>> recommendations =
        recipeRecommendationService.recommendFor(
            distinctDishIds(details),
            userId,
            null,
            RecipeRecommendationService.DEFAULT_RECIPES_PER_DISH);

    Map<LocalDate, List<MenuDetail>> byDate = new LinkedHashMap<>();
    for (MenuDetail detail : details) {
      byDate.computeIfAbsent(detail.getDate(), key -> new ArrayList<>()).add(detail);
    }
    List<MenuMealPlanDayResponse> days =
        byDate.entrySet().stream()
            .map(
                entry ->
                    MenuMealPlanDayResponse.builder()
                        .date(entry.getKey())
                        .meals(
                            entry.getValue().stream()
                                .map(detail -> toMealDetail(detail, recommendations))
                                .toList())
                        .build())
            .toList();

    return MenuMealPlanResponse.builder()
        .id(menu.getId())
        .startDate(menu.getStartDate())
        .endDate(menu.getEndDate())
        .status(menu.getStatus().name())
        .notes(menu.getNotes())
        .createdAt(menu.getCreatedAt())
        .updatedAt(menu.getUpdatedAt())
        .days(days)
        .build();
  }

  /** Distinct dish ids of a plan, so the recommendation lookup is one query per menu. */
  private static List<UUID> distinctDishIds(List<MenuDetail> details) {
    return details.stream().map(detail -> detail.getDish().getId()).distinct().toList();
  }

  /**
   * The dish is embedded in full rather than referenced by id, so a plan can be read aloud. Its
   * recommended recipes come from the batch lookup, never a per-meal query.
   */
  private MealDetailResponse toMealDetail(
      MenuDetail detail, Map<UUID, List<Recipe>> recommendations) {
    Dish dish = detail.getDish();
    return MealDetailResponse.builder()
        .mealType(detail.getMealType().name())
        .servings(detail.getServings())
        .dish(
            DishResponse.builder()
                .id(dish.getId())
                .name(dish.getName())
                .description(dish.getDescription())
                .imageUrl(dish.getImageUrl())
                .cuisineType(dish.getCuisineType())
                .build())
        .recommendedRecipes(
            recommendations.getOrDefault(dish.getId(), List.of()).stream()
                .map(RecipeSummaryMapper::toSummary)
                .toList())
        .build();
  }

  private Sort parseSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return Sort.by(Sort.Direction.DESC, DEFAULT_SORT_PROPERTY);
    }
    String[] parts = sort.split(",");
    if (parts.length == 0 || parts.length > 2) {
      throw new ValidationException("Sort must be in the form property,asc|desc");
    }
    String property = parts[0].trim();
    if (property.isBlank()) {
      throw new ValidationException("Sort property must not be blank");
    }
    Sort.Direction direction =
        parts.length == 2 && parts[1].trim().equalsIgnoreCase("desc")
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    return Sort.by(direction, property);
  }
}
