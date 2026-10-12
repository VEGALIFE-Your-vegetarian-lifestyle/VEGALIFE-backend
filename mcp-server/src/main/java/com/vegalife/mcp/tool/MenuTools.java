package com.vegalife.mcp.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.mcp.client.BackendCallException;
import com.vegalife.mcp.client.BackendClient;
import com.vegalife.mcp.identity.CallerContext;
import com.vegalife.mcp.identity.MissingCallerIdentityException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
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
