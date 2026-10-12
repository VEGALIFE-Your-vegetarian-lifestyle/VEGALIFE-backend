package com.vegalife.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.mcp.client.BackendCallException;
import com.vegalife.mcp.client.BackendClient;
import com.vegalife.mcp.identity.CallerContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Behavioral tests for the four menu tools against the contract in docs/apis/mcp/post-mcp.md: deep
 * mode, parameter validation, the update pre-check, identity errors, and error relay.
 */
class MenuToolsTest {

  private static final String LIST_ONE =
      "{\"success\":true,\"message\":\"Menus retrieved successfully\",\"data\":{\"content\":"
          + "[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"startDate\":\"2026-10-05\","
          + "\"endDate\":\"2026-10-11\",\"status\":\"drafted\"}],\"totalElements\":1}}";

  private static final String LIST_EMPTY =
      "{\"success\":true,\"message\":\"Menus retrieved successfully\",\"data\":{\"content\":[],"
          + "\"totalElements\":0}}";

  private static final String LIST_TWO =
      "{\"success\":true,\"message\":\"Menus retrieved successfully\",\"data\":{\"content\":"
          + "[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"status\":\"drafted\"},"
          + "{\"id\":\"22222222-2222-2222-2222-222222222222\",\"status\":\"scheduled\"}],"
          + "\"totalElements\":2}}";

  private static final String DETAIL =
      "{\"success\":true,\"message\":\"Menu retrieved successfully\",\"data\":"
          + "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"days\":[]}}";

  private static final CallerContext CALLER = new CallerContext("test-jwt");

  private BackendClient backendClient;
  private MenuTools menuTools;

  @BeforeEach
  void setUp() {
    backendClient = mock(BackendClient.class);
    menuTools = new MenuTools(backendClient, new ObjectMapper());
  }

  @Test
  void uniqueListMatchIsDeepenedIntoDetail() {
    when(backendClient.getMenus(eq(CALLER), anyMap())).thenReturn(LIST_ONE);
    when(backendClient.getMenuById(CALLER, "11111111-1111-1111-1111-111111111111"))
        .thenReturn(DETAIL);

    String result = menuTools.getMenu(null, null, null, contextWithIdentity());

    assertEquals(DETAIL, result);
    verify(backendClient).getMenuById(CALLER, "11111111-1111-1111-1111-111111111111");
  }

  @Test
  void emptyListBecomesExplicitNoMenuResult() {
    when(backendClient.getMenus(eq(CALLER), anyMap())).thenReturn(LIST_EMPTY);

    String result = menuTools.getMenu(null, null, null, contextWithIdentity());

    assertEquals(MenuTools.NO_MENU_RESULT, result);
    verify(backendClient, never()).getMenuById(any(), any());
  }

  @Test
  void multipleMatchesKeepTheListSummary() {
    when(backendClient.getMenus(eq(CALLER), anyMap())).thenReturn(LIST_TWO);

    String result = menuTools.getMenu(null, null, null, contextWithIdentity());

    assertEquals(LIST_TWO, result);
    verify(backendClient, never()).getMenuById(any(), any());
  }

  @Test
  void weekAndStatusAreForwardedAsQueryParameters() {
    when(backendClient.getMenus(eq(CALLER), anyMap())).thenReturn(LIST_EMPTY);

    menuTools.getMenu("2026-10-05", "drafted", null, contextWithIdentity());

    verify(backendClient)
        .getMenus(CALLER, Map.of("period", "week", "date", "2026-10-05", "status", "drafted"));
  }

  @Test
  void menuIdSkipsDiscovery() {
    when(backendClient.getMenuById(CALLER, "33333333-3333-3333-3333-333333333333"))
        .thenReturn(DETAIL);

    String result =
        menuTools.getMenu(
            "not-a-date", "bogus", " 33333333-3333-3333-3333-333333333333 ", contextWithIdentity());

    assertEquals(DETAIL, result);
    verify(backendClient, never()).getMenus(any(), anyMap());
  }

  @Test
  void malformedWeekIsRefusedBeforeAnyBackendCall() {
    String result = menuTools.getMenu("05-10-2026", null, null, contextWithIdentity());

    assertEquals("error: week must be a date in YYYY-MM-DD format", result);
    verify(backendClient, never()).getMenus(any(), anyMap());
  }

  @Test
  void unknownStatusIsRefusedBeforeAnyBackendCall() {
    String result = menuTools.getMenu(null, "paused", null, contextWithIdentity());

    assertEquals("error: status must be one of drafted, scheduled, cancelled, completed", result);
    verify(backendClient, never()).getMenus(any(), anyMap());
  }

  @Test
  void missingIdentityIsAReadableResultWithoutBackendCall() {
    String result = menuTools.getMenu(null, null, null, new ToolContext(Map.of()));

    assertTrue(result.startsWith("error: caller identity missing"), result);
    verify(backendClient, never()).getMenus(any(), anyMap());
  }

  @Test
  void backendFailureIsRelayedAsReadableMessage() {
    when(backendClient.getMenus(eq(CALLER), anyMap()))
        .thenThrow(new BackendCallException(401, "Unauthorized"));

    String result = menuTools.getMenu(null, null, null, contextWithIdentity());

    assertEquals("backend call failed with HTTP 401: Unauthorized", result);
  }

  @Test
  void unparseableListPayloadIsRelayedUnchanged() {
    when(backendClient.getMenus(eq(CALLER), anyMap())).thenReturn("<html>502</html>");

    String result = menuTools.getMenu(null, null, null, contextWithIdentity());

    assertEquals("<html>502</html>", result);
    verify(backendClient, never()).getMenuById(any(), any());
    assertFalse(result.isEmpty());
  }

  @Test
  void createMenuForwardsTheComposedBody() {
    when(backendClient.createMenu(eq(CALLER), anyString())).thenReturn("{\"success\":true}");
    List<MenuTools.Day> days =
        List.of(
            new MenuTools.Day("2026-10-19", List.of(new MenuTools.Meal("BREAKFAST", "dish-1", 2))));

    String result =
        menuTools.createMenu(
            "2026-10-19", days, "high-protein", "party week", contextWithIdentity());

    assertEquals("{\"success\":true}", result);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(backendClient).createMenu(eq(CALLER), body.capture());
    try {
      JsonNode sent = new ObjectMapper().readTree(body.getValue());
      assertEquals("2026-10-19", sent.get("startDate").asText());
      assertEquals(1, sent.get("days").size());
      assertEquals("2026-10-19", sent.path("days").get(0).path("date").asText());
      assertEquals(
          "BREAKFAST", sent.path("days").get(0).path("meals").get(0).path("mealType").asText());
      assertEquals("dish-1", sent.path("days").get(0).path("meals").get(0).path("dishId").asText());
      assertEquals(2, sent.path("days").get(0).path("meals").get(0).path("servings").asInt());
      assertEquals("high-protein", sent.get("dietaryPreferences").asText());
      assertEquals("party week", sent.get("notes").asText());
    } catch (JsonProcessingException e) {
      throw new AssertionError("captured body is not JSON", e);
    }
  }

  @Test
  void createMenuOmitsBlankOptionals() {
    when(backendClient.createMenu(eq(CALLER), anyString())).thenReturn("{\"success\":true}");

    menuTools.createMenu(
        "2026-10-19",
        List.of(new MenuTools.Day("2026-10-19", List.of())),
        null,
        " ",
        contextWithIdentity());

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(backendClient).createMenu(eq(CALLER), body.capture());
    try {
      JsonNode sent = new ObjectMapper().readTree(body.getValue());
      assertFalse(sent.has("dietaryPreferences"));
      assertFalse(sent.has("notes"));
    } catch (JsonProcessingException e) {
      throw new AssertionError("captured body is not JSON", e);
    }
  }

  @Test
  void createMenuRejectsMalformedStartDate() {
    String result =
        menuTools.createMenu(
            "19-10-2026",
            List.of(new MenuTools.Day("2026-10-19", List.of())),
            null,
            null,
            contextWithIdentity());

    assertEquals("error: startDate must be a date in YYYY-MM-DD format", result);
    verify(backendClient, never()).createMenu(any(), any());
  }

  @Test
  void updateMenuPassesPreCheckAndPatches() {
    when(backendClient.getMenus(eq(CALLER), anyMap())).thenReturn(LIST_ONE);
    when(backendClient.updateMenu(eq(CALLER), anyString(), anyString()))
        .thenReturn("{\"success\":true}");

    String result =
        menuTools.updateMenu(
            "11111111-1111-1111-1111-111111111111",
            List.of(
                new MenuTools.Day("2026-10-05", List.of(new MenuTools.Meal("LUNCH", "d-2", 1)))),
            null,
            "new note",
            contextWithIdentity());

    assertEquals("{\"success\":true}", result);
    verify(backendClient).getMenus(CALLER, Map.of("period", "week", "status", "drafted"));
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(backendClient)
        .updateMenu(eq(CALLER), eq("11111111-1111-1111-1111-111111111111"), body.capture());
    try {
      JsonNode sent = new ObjectMapper().readTree(body.getValue());
      assertFalse(sent.has("startDate"));
      assertEquals(
          "LUNCH", sent.path("days").get(0).path("meals").get(0).path("mealType").asText());
      assertEquals("new note", sent.get("notes").asText());
    } catch (JsonProcessingException e) {
      throw new AssertionError("captured body is not JSON", e);
    }
  }

  @Test
  void updateMenuRefusedWhenTargetNotInCurrentWeekDrafts() {
    when(backendClient.getMenus(eq(CALLER), anyMap())).thenReturn(LIST_EMPTY);

    String result =
        menuTools.updateMenu(
            "99999999-9999-9999-9999-999999999999", List.of(), null, null, contextWithIdentity());

    assertEquals(MenuTools.UPDATE_REFUSED, result);
    verify(backendClient, never()).updateMenu(any(), any(), any());
  }

  @Test
  void updateMenuRefusedWhenDraftListUnparseable() {
    when(backendClient.getMenus(eq(CALLER), anyMap())).thenReturn("<html>502</html>");

    String result =
        menuTools.updateMenu(
            "11111111-1111-1111-1111-111111111111", List.of(), null, null, contextWithIdentity());

    assertEquals(
        MenuTools.UPDATE_REFUSED + " (the current-week draft could not be verified)", result);
    verify(backendClient, never()).updateMenu(any(), any(), any());
  }

  @Test
  void scheduleMenuPostsScheduledStatus() {
    when(backendClient.updateMenuStatus(eq(CALLER), anyString(), anyString()))
        .thenReturn("{\"success\":true}");

    String result =
        menuTools.scheduleMenu("11111111-1111-1111-1111-111111111111", contextWithIdentity());

    assertEquals("{\"success\":true}", result);
    verify(backendClient)
        .updateMenuStatus(
            CALLER, "11111111-1111-1111-1111-111111111111", "{\"status\":\"scheduled\"}");
  }

  @Test
  void writeToolsRequireMenuIdOrStartDate() {
    assertEquals(
        "error: menuId is required",
        menuTools.updateMenu(" ", List.of(), null, null, contextWithIdentity()));
    assertEquals("error: menuId is required", menuTools.scheduleMenu(null, contextWithIdentity()));
    assertEquals(
        "error: startDate must be a date in YYYY-MM-DD format",
        menuTools.createMenu(null, List.of(), null, null, contextWithIdentity()));
    verify(backendClient, never()).updateMenu(any(), any(), any());
    verify(backendClient, never()).updateMenuStatus(any(), any(), any());
    verify(backendClient, never()).createMenu(any(), any());
  }

  private static ToolContext contextWithIdentity() {
    return new ToolContext(Map.of(CallerContext.META_KEY, CALLER.jwt()));
  }
}
