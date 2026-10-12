package com.vegalife.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.mcp.client.BackendCallException;
import com.vegalife.mcp.client.BackendClient;
import com.vegalife.mcp.identity.CallerContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Behavioral tests for {@code get_menu} against the contract in docs/apis/mcp/post-mcp.md: deep
 * mode, parameter validation, identity errors, and error relay.
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

  private static ToolContext contextWithIdentity() {
    return new ToolContext(Map.of(CallerContext.META_KEY, CALLER.jwt()));
  }
}
