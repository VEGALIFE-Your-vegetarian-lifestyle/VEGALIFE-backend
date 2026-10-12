package com.vegalife.mcp.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vegalife.mcp.client.BackendCallException;
import com.vegalife.mcp.client.BackendClient;
import com.vegalife.mcp.identity.CallerContext;
import com.vegalife.mcp.identity.MissingCallerIdentityException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  /** Result text for an empty week, as promised by the tool contract. */
  static final String NO_MENU_RESULT = "no menu for that week";

  private static final Set<String> STATUSES =
      Set.of("drafted", "scheduled", "cancelled", "completed");

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
          "Read the caller's weekly meal menu(s). Without menuId, lists the menus of one week"
              + " (the week containing the given date, or the current week in server UTC) and"
              + " returns the full 7-day detail when exactly one menu matches; zero matches"
              + " returns an explicit no-menu result, several matches return the list summary."
              + " With menuId, returns that menu's 7-day detail directly. The caller's identity"
              + " comes from their session and is never a parameter.")
  public String getMenu(
      @ToolParam(
              required = false,
              description =
                  "Any date in the target week, format YYYY-MM-DD. Omit for the current"
                      + " week (server UTC).")
          String week,
      @ToolParam(
              required = false,
              description =
                  "Filter by status: drafted, scheduled, cancelled or completed. Omit"
                      + " for any status.")
          String status,
      @ToolParam(
              required = false,
              description = "UUID of a specific owned menu to read directly, skipping discovery.")
          String menuId,
      ToolContext toolContext) {
    try {
      CallerContext caller = CallerContext.require(toolContext);

      if (menuId != null && !menuId.isBlank()) {
        return backendClient.getMenuById(caller, menuId.trim());
      }

      if (week != null && !week.isBlank() && !isIsoDate(week.trim())) {
        return "error: week must be a date in YYYY-MM-DD format";
      }

      String statusFilter = (status == null || status.isBlank()) ? null : status.trim();
      if (statusFilter != null && !STATUSES.contains(statusFilter)) {
        return "error: status must be one of drafted, scheduled, cancelled, completed";
      }

      Map<String, String> query = new LinkedHashMap<>();
      query.put("period", "week");
      if (week != null && !week.isBlank()) {
        query.put("date", week.trim());
      }
      if (statusFilter != null) {
        query.put("status", statusFilter);
      }

      String listJson = backendClient.getMenus(caller, query);
      return deepenUniqueMatch(caller, listJson);
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
   * The current-week window is whatever the backend returns for {@code period=week} — server UTC
   * stays the single time authority; this method only compares membership and status. Returns null
   * when the pre-check passes, or the refusal message.
   */
  private String preCheckCurrentWeekDraft(CallerContext caller, String menuId) {
    String listJson = backendClient.getMenus(caller, Map.of("period", "week", "status", "drafted"));
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

  /**
   * Deep mode (FR-003): a list with exactly one match is replaced by that menu's full 7-day detail;
   * zero matches becomes an explicit result; several matches keep the list summary. An
   * unrecognizable payload is relayed unchanged rather than dropped.
   */
  private String deepenUniqueMatch(CallerContext caller, String listJson) {
    JsonNode content = listContent(listJson);
    if (content == null) {
      return listJson;
    }
    if (content.isEmpty()) {
      return NO_MENU_RESULT;
    }
    if (content.size() == 1 && content.get(0).hasNonNull("id")) {
      return backendClient.getMenuById(caller, content.get(0).get("id").asText());
    }
    return listJson;
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

  private static boolean isIsoDate(String value) {
    try {
      LocalDate.parse(value);
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }
}
