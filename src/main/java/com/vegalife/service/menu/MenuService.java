package com.vegalife.service.menu;

import com.vegalife.dto.response.menu.MealResponse;
import com.vegalife.dto.response.menu.MenuDayResponse;
import com.vegalife.dto.response.menu.MenuDetailResponse;
import com.vegalife.dto.response.menu.MenuSummaryResponse;
import com.vegalife.model.menu.Menu;
import com.vegalife.model.menu.MenuDetail;
import com.vegalife.repository.menu.MenuDetailRepository;
import com.vegalife.repository.menu.MenuRepository;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only menu queries: the caller's own menus filtered by a time window, and one menu detail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

  private static final String DEFAULT_SORT_PROPERTY = "startDate";

  private final MenuRepository menuRepository;
  private final MenuDetailRepository menuDetailRepository;

  /**
   * BR-MENU-001 / BR-MENU-002: the caller's own menus intersecting the filter window, optionally by
   * status, newest start date first. The owner comes from the JWT, never a request parameter.
   */
  @Transactional(readOnly = true)
  public PageResponse<MenuSummaryResponse> listMenus(
      UUID userId, MenuQueryFilter filter, int page, int size, String sort) {
    Pageable pageable = PageRequest.of(page, size, parseSort(sort));
    Page<Menu> menus =
        menuRepository.findOwnMenus(
            userId, filter.getFrom(), filter.getTo(), filter.getStatus(), pageable);
    return PageResponse.from(menus.map(this::toSummary));
  }

  /**
   * BR-MENU-001: one owned menu with its days, ordered by date and meal type. A missing or foreign
   * id is a 404, never 403.
   */
  @Transactional(readOnly = true)
  public MenuDetailResponse getMenu(UUID userId, UUID menuId) {
    Menu menu =
        menuRepository
            .findByIdAndUserId(menuId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Menu not found"));

    List<MenuDetail> details = menuDetailRepository.findByMenuIdOrderByDateAscMealTypeAsc(menuId);
    return toDetail(menu, details);
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
        .dietaryPreferences(menu.getDietaryPreferences())
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
