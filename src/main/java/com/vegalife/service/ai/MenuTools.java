package com.vegalife.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.request.menu.CreateMenuRequest;
import com.vegalife.dto.request.menu.MealRequest;
import com.vegalife.dto.request.menu.MenuDayRequest;
import com.vegalife.dto.request.menu.UpdateMenuRequest;
import com.vegalife.service.menu.MenuQueryFilter;
import com.vegalife.service.menu.MenuQueryFilterResolver;
import com.vegalife.service.menu.MenuService;
import com.vegalife.shared.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * The weekly-menu tools the AI chat can call (BR-MCP-002). Each is a thin adapter over {@link
 * MenuService}: the service owns every rule and every date computation, and this class only maps
 * tool parameters onto a service call and relays the result.
 *
 * <p>Serving these in-process (ADR-013) rather than through a separate MCP server keeps the tool
 * contract — names, descriptions, parameter schemas, and the readable {@code "error: ..."} result
 * convention — exactly as it was. Identity always comes from the caller's authenticated session via
 * {@link ToolContext} (BR-MCP-004), never from a tool parameter.
 *
 * <p>Contract: {@code docs/apis/mcp/post-mcp.md}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuTools {

  /** Refusal text of the update_menu pre-check (BR-MCP-005). */
  static final String UPDATE_REFUSED =
      "update refused: the target must be the caller's own current-week menu with status drafted";

  private static final String MISSING_IDENTITY =
      "error: caller identity missing (no caller identity on this tool invocation)";

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 100;

  private final MenuService menuService;
  private final MenuQueryFilterResolver filterResolver;
  private final Validator validator;
  private final ObjectMapper objectMapper;

  @Tool(
      name = "get_menu",
      resultConverter = RawTextToolResultConverter.class,
      description =
          "List the caller's weekly menus over a date range. Mirrors GET /api/menus exactly:"
              + " from/to are inclusive bounds and either may be supplied alone, status filters by"
              + " drafted, scheduled, cancelled or completed, and the result is a paginated list of"
              + " menu summaries ordered by startDate descending. Each summary carries the menu's"
              + " id, window, status and notes but not its meals — call get_meal_plan for the"
              + " day-by-day dishes. The caller's identity comes from their session and is never a"
              + " parameter.")
  public String getMenu(
      @ToolParam(
              required = false,
              description = "Inclusive start of the range, format YYYY-MM-DD. Omit for unbounded.")
          String from,
      @ToolParam(
              required = false,
              description = "Inclusive end of the range, format YYYY-MM-DD. Omit for unbounded.")
          String to,
      @ToolParam(
              required = false,
              description =
                  "Filter by menu status: drafted, scheduled, cancelled or completed. Omit for"
                      + " any status.")
          String status,
      @ToolParam(required = false, description = "Zero-based page index. Omit for 0.") Integer page,
      @ToolParam(required = false, description = "Menus per page, 1 to 100. Omit for 20.")
          Integer size,
      @ToolParam(
              required = false,
              description = "Sort as 'field,direction', e.g. 'startDate,desc'.")
          String sort,
      ToolContext toolContext) {
    UUID userId = callerUserId(toolContext);
    if (userId == null) {
      return MISSING_IDENTITY;
    }
    try {
      MenuQueryFilter filter = filterResolver.resolve(from, to, status);
      return write(
          menuService.listMenus(
              userId,
              filter,
              page == null ? DEFAULT_PAGE : Math.max(0, page),
              size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size)),
              sort),
          "Menus retrieved successfully");
    } catch (RuntimeException e) {
      return toolFailure(e);
    }
  }

  @Tool(
      name = "get_meal_plan",
      resultConverter = RawTextToolResultConverter.class,
      description =
          "Read the caller's meal plan over a date range: every menu intersecting the range, each"
              + " with its days, meals and the full dish behind each meal (name, description,"
              + " image, cuisine). from/to are inclusive bounds and either may be supplied alone;"
              + " with neither, the range defaults to the current week (Monday to Sunday, server"
              + " UTC). menuId reads exactly one menu's meals and overrides any range supplied"
              + " with it. There is no status filter — every returned menu carries its own status."
              + " Use get_menu instead for a paginated list of menu summaries without meals. The"
              + " caller's identity comes from their session and is never a parameter.")
  public String getMealPlan(
      @ToolParam(
              required = false,
              description =
                  "Inclusive start of the range, format YYYY-MM-DD. Omit for the current"
                      + " week when to is also omitted.")
          String from,
      @ToolParam(
              required = false,
              description =
                  "Inclusive end of the range, format YYYY-MM-DD. Omit for the current"
                      + " week when from is also omitted.")
          String to,
      @ToolParam(
              required = false,
              description = "UUID of one specific owned menu to read directly. Overrides from/to.")
          String menuId,
      ToolContext toolContext) {
    UUID userId = callerUserId(toolContext);
    if (userId == null) {
      return MISSING_IDENTITY;
    }
    UUID id = parseUuid(menuId);
    if (id == INVALID_UUID) {
      return "error: menuId must be a UUID";
    }
    try {
      MenuQueryFilter filter = filterResolver.resolveMealPlan(from, to);
      return write(
          menuService.listMealPlans(userId, filter, id), "Meal plan retrieved successfully");
    } catch (RuntimeException e) {
      return toolFailure(e);
    }
  }

  @Tool(
      name = "create_menu",
      resultConverter = RawTextToolResultConverter.class,
      description =
          "Create the caller's weekly menu as a drafted plan. Pass startDate (the week's first"
              + " day) and days: exactly 7 day objects, each {date, meals[]}, meal = {mealType,"
              + " dishId, servings} with mealType BREAKFAST, LUNCH or DINNER. All structural"
              + " rules (7 dates, one meal per type per day, dish diversity) are validated by the"
              + " backend, which returns a readable error when the week is invalid. Returns the"
              + " created menu's full detail with its new id. The caller's identity comes from"
              + " their session and is never a parameter.")
  public String createMenu(
      @ToolParam(
              required = true,
              description = "First day of the week, format YYYY-MM-DD (a Monday by convention).")
          String startDate,
      @ToolParam(
              required = true,
              description =
                  "Exactly 7 day objects: {date: YYYY-MM-DD, meals: [{mealType, dishId,"
                      + " servings}]}.")
          List<Day> days,
      @ToolParam(required = false, description = "Free-text dietary preference for the week.")
          String dietaryPreferences,
      @ToolParam(required = false, description = "Free-text note for the menu.") String notes,
      ToolContext toolContext) {
    UUID userId = callerUserId(toolContext);
    if (userId == null) {
      return MISSING_IDENTITY;
    }
    LocalDate start = parseIsoDate(startDate);
    if (start == null) {
      return "error: startDate must be a date in YYYY-MM-DD format";
    }
    List<MenuDayRequest> dayRequests;
    try {
      dayRequests = toDayRequests(days);
    } catch (IllegalArgumentException e) {
      return "error: " + e.getMessage();
    }

    CreateMenuRequest request =
        CreateMenuRequest.builder()
            .startDate(start)
            .dietaryPreferences(blankToNull(dietaryPreferences))
            .notes(blankToNull(notes))
            .days(dayRequests)
            .build();
    try {
      validate(request);
      return write(menuService.createMenu(userId, request), "Menu created successfully");
    } catch (RuntimeException e) {
      return toolFailure(e);
    }
  }

  @Tool(
      name = "update_menu",
      resultConverter = RawTextToolResultConverter.class,
      description =
          "Replace the days of the caller's current-week drafted menu. Meals are replaced"
              + " wholesale. Refused unless the target is the caller's own menu of the current"
              + " week with status drafted; dietaryPreferences and notes, when passed, replace"
              + " the stored values. The backend re-validates the whole week. Returns the"
              + " updated menu's full detail.")
  public String updateMenu(
      @ToolParam(
              required = true,
              description = "UUID of the menu to replace: the caller's current-week drafted menu.")
          String menuId,
      @ToolParam(
              required = true,
              description =
                  "The complete new week: exactly 7 day objects {date, meals[]}; meals are"
                      + " replaced wholesale.")
          List<Day> days,
      @ToolParam(
              required = false,
              description = "New dietary preference; omit to keep the stored value.")
          String dietaryPreferences,
      @ToolParam(required = false, description = "New note; omit to keep the stored value.")
          String notes,
      ToolContext toolContext) {
    UUID userId = callerUserId(toolContext);
    if (userId == null) {
      return MISSING_IDENTITY;
    }
    UUID id = requireMenuId(menuId);
    if (id == null) {
      return "error: menuId is required";
    }

    String refusal = preCheckCurrentWeekDraft(userId, id);
    if (refusal != null) {
      return refusal;
    }

    List<MenuDayRequest> dayRequests;
    try {
      dayRequests = toDayRequests(days);
    } catch (IllegalArgumentException e) {
      return "error: " + e.getMessage();
    }

    UpdateMenuRequest request =
        UpdateMenuRequest.builder()
            .days(dayRequests)
            .dietaryPreferences(blankToNull(dietaryPreferences))
            .notes(blankToNull(notes))
            .build();
    try {
      validate(request);
      return write(menuService.updateMenu(userId, id, request), "Menu updated successfully");
    } catch (RuntimeException e) {
      return toolFailure(e);
    }
  }

  @Tool(
      name = "schedule_menu",
      resultConverter = RawTextToolResultConverter.class,
      description =
          "Schedule (activate) the caller's drafted weekly menu so it becomes the live plan."
              + " Call only after the user has explicitly confirmed the exact week. Returns the"
              + " menu's full detail with status scheduled.")
  public String scheduleMenu(
      @ToolParam(required = true, description = "UUID of the caller's drafted menu to schedule.")
          String menuId,
      ToolContext toolContext) {
    UUID userId = callerUserId(toolContext);
    if (userId == null) {
      return MISSING_IDENTITY;
    }
    UUID id = requireMenuId(menuId);
    if (id == null) {
      return "error: menuId is required";
    }
    try {
      return write(
          menuService.changeStatus(userId, id, "scheduled"), "Menu status updated successfully");
    } catch (RuntimeException e) {
      return toolFailure(e);
    }
  }

  /** One day of a menu week as the model composes it; mapped onto the service's own DTO. */
  public record Day(String date, List<Meal> meals) {}

  /** One meal slot of a day; mealType is BREAKFAST, LUNCH or DINNER (the service validates). */
  public record Meal(String mealType, String dishId, Integer servings) {}

  /**
   * BR-MCP-005 pre-check: update_menu may target only the caller's own current-week drafted menu.
   * The backend no longer resolves a week for us (BR-MENU-002 is a flat from/to pair), so this
   * computes the Monday–Sunday window of the current server-UTC day and asks for that range; it
   * only compares membership and status. Returns null when the pre-check passes, or the refusal
   * message.
   */
  private String preCheckCurrentWeekDraft(UUID userId, UUID menuId) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    try {
      MenuQueryFilter currentWeek =
          filterResolver.resolve(
              today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(),
              today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toString(),
              "drafted");
      var page = menuService.listMenus(userId, currentWeek, DEFAULT_PAGE, DEFAULT_SIZE, null);
      for (var summary : page.getContent()) {
        if (menuId.equals(summary.getId())) {
          return null;
        }
      }
      return UPDATE_REFUSED;
    } catch (RuntimeException e) {
      log.debug("current-week draft could not be verified for the update_menu pre-check", e);
      return UPDATE_REFUSED + " (the current-week draft could not be verified)";
    }
  }

  /**
   * Runs the same Jakarta bean validation the REST layer applies through {@code @Valid}, so a tool
   * call cannot reach the service with a payload the HTTP path would have rejected.
   */
  private void validate(Object request) {
    Set<ConstraintViolation<Object>> violations = validator.validate(request);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  /** Maps the model's day objects onto the service DTOs, parsing dates and dish ids. */
  private List<MenuDayRequest> toDayRequests(List<Day> days) {
    List<MenuDayRequest> mapped = new ArrayList<>();
    if (days == null) {
      return mapped;
    }
    for (int i = 0; i < days.size(); i++) {
      Day day = days.get(i);
      if (day == null) {
        throw new IllegalArgumentException("days[" + i + "] must be an object");
      }
      LocalDate date = parseIsoDate(day.date());
      if (date == null) {
        throw new IllegalArgumentException(
            "days[" + i + "].date must be a date in YYYY-MM-DD format");
      }
      List<MealRequest> meals = new ArrayList<>();
      List<Meal> sourceMeals = day.meals() == null ? List.of() : day.meals();
      for (int j = 0; j < sourceMeals.size(); j++) {
        meals.add(toMealRequest(i, j, sourceMeals.get(j)));
      }
      mapped.add(MenuDayRequest.builder().date(date).meals(meals).build());
    }
    return mapped;
  }

  private MealRequest toMealRequest(int dayIndex, int mealIndex, Meal meal) {
    if (meal == null) {
      throw new IllegalArgumentException(
          "days[" + dayIndex + "].meals[" + mealIndex + "] must be an object");
    }
    String rawDishId = meal.dishId() == null ? "" : meal.dishId().trim();
    UUID dishId = parseUuid(rawDishId);
    if (dishId == null || dishId == INVALID_UUID) {
      throw new IllegalArgumentException(
          "days[" + dayIndex + "].meals[" + mealIndex + "].dishId must be a UUID");
    }
    return MealRequest.builder()
        .mealType(meal.mealType())
        .dishId(dishId)
        .servings(meal.servings())
        .build();
  }

  private UUID callerUserId(ToolContext toolContext) {
    if (toolContext == null || toolContext.getContext() == null) {
      return null;
    }
    Object value = toolContext.getContext().get(AiToolContext.USER_ID_KEY);
    return value instanceof UUID userId ? userId : null;
  }

  /** Sentinel for "present but unparseable", distinct from "absent". */
  private static final UUID INVALID_UUID = new UUID(0L, 0L);

  private UUID parseUuid(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      return INVALID_UUID;
    }
  }

  private UUID requireMenuId(String raw) {
    UUID id = parseUuid(raw);
    return id == null || id == INVALID_UUID ? null : id;
  }

  private static LocalDate parseIsoDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(raw.trim());
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** One-line, model-readable relay of a domain failure (BR-MCP-007). Never carries credentials. */
  private String toolFailure(RuntimeException e) {
    String message = e.getMessage();
    if (e instanceof ConstraintViolationException cve) {
      message =
          cve.getConstraintViolations().stream()
              .map(ConstraintViolation::getMessage)
              .sorted()
              .collect(Collectors.joining(", "));
    }
    if (message == null || message.isBlank()) {
      message = e.getClass().getSimpleName();
    }
    return "error: " + message;
  }

  /**
   * The success payload the model sees: the backend's own {@code {success, message, data}}
   * envelope, exactly what these tools relayed while they called the REST API over MCP. The message
   * is the controller's own, so a tool result and the matching HTTP response read the same.
   */
  private String write(Object payload, String message) {
    try {
      return objectMapper.writeValueAsString(ApiResponse.success(payload, message));
    } catch (JsonProcessingException e) {
      // A service DTO of plain values cannot fail to serialize; guard for the compiler anyway.
      throw new IllegalStateException("failed to serialize the menu tool result", e);
    }
  }
}
