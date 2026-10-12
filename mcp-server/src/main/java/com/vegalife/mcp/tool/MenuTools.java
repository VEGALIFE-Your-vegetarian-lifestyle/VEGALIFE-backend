package com.vegalife.mcp.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vegalife.mcp.client.BackendCallException;
import com.vegalife.mcp.client.BackendClient;
import com.vegalife.mcp.identity.CallerContext;
import com.vegalife.mcp.identity.MissingCallerIdentityException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The weekly-menu tools exposed to the AI chat (BR-MCP-002). Each tool is a thin adapter over the
 * backend's menu REST endpoints: the backend owns every rule and every date computation — this
 * class only maps parameters to an endpoint call and relays the response.
 *
 * <p>Contract: {@code docs/apis/mcp/post-mcp.md}. Identity always comes from the caller's session
 * (BR-MCP-004), never from a tool parameter.
 */
public class MenuTools {

  private static final Set<String> STATUSES =
      Set.of("drafted", "scheduled", "cancelled", "completed");

  private static final Set<String> SORT_DIRECTIONS = Set.of("asc", "desc");

  /** Refusal text of the update_menu pre-check (BR-MCP-005). */
  static final String UPDATE_REFUSED =
      "update refused: the target must be the caller's own current-week menu with status drafted";

  private final BackendClient backendClient;
  private final ObjectMapper objectMapper;

  public MenuTools(BackendClient backendClient, ObjectMapper objectMapper) {
    this.backendClient = backendClient;
    this.objectMapper = objectMapper;
  }

  @Tool(
      name = "get_menu",
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
    try {
      CallerContext caller = CallerContext.require(toolContext);

      String badDate = firstMalformedDate(from, to);
      if (badDate != null) {
        return "error: " + badDate + " must be a date in YYYY-MM-DD format";
      }

      String statusFilter = (status == null || status.isBlank()) ? null : status.trim();
      if (statusFilter != null && !STATUSES.contains(statusFilter)) {
        return "error: status must be one of drafted, scheduled, cancelled, completed";
      }

      String badSort = checkSort(sort);
      if (badSort != null) {
        return "error: " + badSort;
      }

      Map<String, String> query = new LinkedHashMap<>();
      putIfPresent(query, "from", from);
      putIfPresent(query, "to", to);
      putIfPresent(query, "status", statusFilter);
      if (page != null) {
        query.put("page", Integer.toString(Math.max(0, page)));
      }
      if (size != null) {
        query.put("size", Integer.toString(Math.min(100, Math.max(1, size))));
      }
      putIfPresent(query, "sort", sort);

      return backendClient.getMenus(caller, query);
    } catch (MissingCallerIdentityException e) {
      return "error: caller identity missing (" + e.getMessage() + ")";
    } catch (BackendCallException e) {
      return e.asToolMessage();
    }
  }

  @Tool(
      name = "get_meal_plan",
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
    try {
      CallerContext caller = CallerContext.require(toolContext);

      String badDate = firstMalformedDate(from, to);
      if (badDate != null) {
        return "error: " + badDate + " must be a date in YYYY-MM-DD format";
      }

      String id = menuId == null ? "" : menuId.trim();
      if (!id.isEmpty() && !isUuid(id)) {
        return "error: menuId must be a UUID";
      }

      Map<String, String> query = new LinkedHashMap<>();
      putIfPresent(query, "from", from);
      putIfPresent(query, "to", to);
      putIfPresent(query, "menuId", id);

      return backendClient.getMealPlan(caller, query);
    } catch (MissingCallerIdentityException e) {
      return "error: caller identity missing (" + e.getMessage() + ")";
    } catch (BackendCallException e) {
      return e.asToolMessage();
    }
  }

  @Tool(
      name = "create_menu",
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
    try {
      CallerContext caller = CallerContext.require(toolContext);

      if (startDate == null || startDate.isBlank() || !isIsoDate(startDate.trim())) {
        return "error: startDate must be a date in YYYY-MM-DD format";
      }

      ObjectNode body = objectMapper.createObjectNode();
      body.put("startDate", startDate.trim());
      body.set("days", objectMapper.valueToTree(days));
      putIfPresent(body, "dietaryPreferences", dietaryPreferences);
      putIfPresent(body, "notes", notes);

      return backendClient.createMenu(caller, writeJson(body));
    } catch (MissingCallerIdentityException e) {
      return "error: caller identity missing (" + e.getMessage() + ")";
    } catch (BackendCallException e) {
      return e.asToolMessage();
    }
  }

  @Tool(
      name = "update_menu",
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
    try {
      CallerContext caller = CallerContext.require(toolContext);

      String id = menuId == null ? "" : menuId.trim();
      if (id.isEmpty()) {
        return "error: menuId is required";
      }

      String refusal = preCheckCurrentWeekDraft(caller, id);
      if (refusal != null) {
        return refusal;
      }

      ObjectNode body = objectMapper.createObjectNode();
      body.set("days", objectMapper.valueToTree(days));
      putIfPresent(body, "dietaryPreferences", dietaryPreferences);
      putIfPresent(body, "notes", notes);

      return backendClient.updateMenu(caller, id, writeJson(body));
    } catch (MissingCallerIdentityException e) {
      return "error: caller identity missing (" + e.getMessage() + ")";
    } catch (BackendCallException e) {
      return e.asToolMessage();
    }
  }

  @Tool(
      name = "schedule_menu",
      description =
          "Schedule (activate) the caller's drafted weekly menu so it becomes the live plan."
              + " Call only after the user has explicitly confirmed the exact week. Returns the"
              + " menu's full detail with status scheduled.")
  public String scheduleMenu(
      @ToolParam(required = true, description = "UUID of the caller's drafted menu to schedule.")
          String menuId,
      ToolContext toolContext) {
    try {
      CallerContext caller = CallerContext.require(toolContext);

      String id = menuId == null ? "" : menuId.trim();
      if (id.isEmpty()) {
        return "error: menuId is required";
      }

      return backendClient.updateMenuStatus(caller, id, "{\"status\":\"scheduled\"}");
    } catch (MissingCallerIdentityException e) {
      return "error: caller identity missing (" + e.getMessage() + ")";
    } catch (BackendCallException e) {
      return e.asToolMessage();
    }
  }

  /** One day of a menu week as the model composes it; forwarded to the backend verbatim. */
  public record Day(String date, List<Meal> meals) {}

  /** One meal slot of a day; mealType is BREAKFAST, LUNCH or DINNER (the backend validates). */
  public record Meal(String mealType, String dishId, Integer servings) {}

  /**
   * BR-MCP-005 pre-check: update_menu may target only the caller's own current-week drafted menu.
   * The backend no longer resolves a week for us (BR-MENU-002 is a flat from/to pair), so this
   * method computes the Monday–Sunday window of the current server-UTC day and asks for that range;
   * it only compares membership and status. Returns null when the pre-check passes, or the refusal
   * message.
   */
  private String preCheckCurrentWeekDraft(CallerContext caller, String menuId) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Map<String, String> query = new LinkedHashMap<>();
    query.put("from", iso(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))));
    query.put("to", iso(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))));
    query.put("status", "drafted");

    String listJson = backendClient.getMenus(caller, query);
    JsonNode content = listContent(listJson);
    if (content == null) {
      return UPDATE_REFUSED + " (the current-week draft could not be verified)";
    }
    for (JsonNode item : content) {
      if (menuId.equals(item.path("id").asText(null))) {
        return null;
      }
    }
    return UPDATE_REFUSED;
  }

  /** Returns the name of the first bound that is present but not an ISO date, else null. */
  private static String firstMalformedDate(String from, String to) {
    if (from != null && !from.isBlank() && !isIsoDate(from.trim())) {
      return "from";
    }
    if (to != null && !to.isBlank() && !isIsoDate(to.trim())) {
      return "to";
    }
    return null;
  }

  /**
   * Mirrors the backend's sort parsing closely enough to fail fast: {@code property} or {@code
   * property,asc|desc}, nothing else. Returns an error message, or null when acceptable.
   */
  private static String checkSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return null;
    }
    String[] parts = sort.split(",");
    if (parts.length == 0 || parts.length > 2 || parts[0].trim().isEmpty()) {
      return "sort must be in the form 'property,asc|desc'";
    }
    if (parts.length == 2 && !SORT_DIRECTIONS.contains(parts[1].trim().toLowerCase())) {
      return "sort direction must be 'asc' or 'desc'";
    }
    return null;
  }

  private static void putIfPresent(Map<String, String> query, String key, String value) {
    if (value != null && !value.isBlank()) {
      query.put(key, value.trim());
    }
  }

  private static void putIfPresent(ObjectNode body, String field, String value) {
    if (value != null && !value.isBlank()) {
      body.put(field, value.trim());
    }
  }

  private String writeJson(JsonNode body) {
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      // An ObjectNode of plain values cannot fail to serialize; guard for the compiler anyway.
      throw new IllegalStateException("failed to serialize the menu request body", e);
    }
  }

  /** Returns {@code data.content} of a list envelope, or null when the shape is unexpected. */
  private JsonNode listContent(String listJson) {
    try {
      JsonNode data = objectMapper.readTree(listJson).path("data");
      JsonNode content = data.path("content");
      return content.isArray() ? content : null;
    } catch (JsonProcessingException notJson) {
      // Not JSON (an HTML error page, a truncated body) — relay it untouched.
      return null;
    }
  }

  private static String iso(LocalDate date) {
    return date.toString();
  }

  private static boolean isIsoDate(String value) {
    try {
      LocalDate.parse(value);
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  private static boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
