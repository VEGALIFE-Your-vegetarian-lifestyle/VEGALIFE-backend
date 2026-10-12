package com.vegalife.mcp.tool;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.vegalife.mcp.client.BackendClient;
import com.vegalife.mcp.identity.CallerContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.web.client.RestClient;

/**
 * Drives the tools through the real {@link BackendClient} against a WireMock backend, proving the
 * over-the-wire request shapes (query parameter names, bearer header, camelCase bodies) that the
 * mock-based {@link MenuToolsTest} cannot see.
 */
class MenuToolsHttpTest {

  private static final CallerContext CALLER = new CallerContext("jwt-abc");

  private static final String LIST_ONE =
      "{\"success\":true,\"message\":\"Menus retrieved successfully\",\"data\":{\"content\":"
          + "[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"startDate\":\"2026-10-19\","
          + "\"endDate\":\"2026-10-25\",\"status\":\"drafted\"}],\"totalElements\":1}}";

  private static final String DETAIL =
      "{\"success\":true,\"message\":\"Menu retrieved successfully\",\"data\":"
          + "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"days\":[]}}";

  private WireMockServer backend;
  private MenuTools menuTools;

  @BeforeEach
  void setUp() {
    backend = new WireMockServer(wireMockConfig().dynamicPort());
    backend.start();
    BackendClient client =
        new BackendClient(
            RestClient.builder().baseUrl(backend.baseUrl()).build(), new ObjectMapper());
    menuTools = new MenuTools(client, new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    backend.stop();
  }

  @Test
  void getMenuDeepensUniqueWeekMatchOverTheWire() {
    backend.stubFor(
        get(urlPathEqualTo("/api/menus"))
            .withQueryParam("period", equalTo("week"))
            .withQueryParam("date", equalTo("2026-10-19"))
            .withHeader("Authorization", equalTo("Bearer " + CALLER.jwt()))
            .willReturn(okJson(LIST_ONE)));
    backend.stubFor(
        get(urlPathEqualTo("/api/menus/11111111-1111-1111-1111-111111111111"))
            .withHeader("Authorization", equalTo("Bearer " + CALLER.jwt()))
            .willReturn(okJson(DETAIL)));

    String result = menuTools.getMenu("2026-10-19", null, null, contextWithIdentity());

    assertEquals(DETAIL, result);
  }

  @Test
  void createMenuSendsCamelCaseWeekBodyWithIdentity() {
    backend.stubFor(
        post(urlPathEqualTo("/api/menus"))
            .withHeader("Authorization", equalTo("Bearer " + CALLER.jwt()))
            .willReturn(okJson("{\"success\":true,\"data\":{\"id\":\"m-1\"}}")));

    String result =
        menuTools.createMenu(
            "2026-10-19",
            List.of(
                new MenuTools.Day(
                    "2026-10-19", List.of(new MenuTools.Meal("BREAKFAST", "d-1", 2)))),
            null,
            "meal prep",
            contextWithIdentity());

    assertTrue(result.contains("m-1"));
    JsonNode sent = parseBody(backend.getAllServeEvents().get(0).getRequest().getBody());
    assertEquals("2026-10-19", sent.get("startDate").asText());
    assertEquals("meal prep", sent.get("notes").asText());
    assertEquals(
        "BREAKFAST", sent.path("days").get(0).path("meals").get(0).path("mealType").asText());
    assertEquals("d-1", sent.path("days").get(0).path("meals").get(0).path("dishId").asText());
    assertEquals(2, sent.path("days").get(0).path("meals").get(0).path("servings").asInt());
  }

  @Test
  void scheduleMenuPostsStatusBodyOverTheWire() {
    backend.stubFor(
        post(urlPathEqualTo("/api/menus/11111111-1111-1111-1111-111111111111/status"))
            .withHeader("Authorization", equalTo("Bearer " + CALLER.jwt()))
            .willReturn(okJson("{\"success\":true}")));

    String result =
        menuTools.scheduleMenu("11111111-1111-1111-1111-111111111111", contextWithIdentity());

    assertEquals("{\"success\":true}", result);
    assertEquals(
        "{\"status\":\"scheduled\"}",
        new String(
            backend.getAllServeEvents().get(0).getRequest().getBody(), StandardCharsets.UTF_8));
  }

  @Test
  void backendUnreachableIsRelayedAsReadableToolMessage() {
    backend.stubFor(
        get(urlPathEqualTo("/api/menus"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    String result = menuTools.getMenu(null, null, null, contextWithIdentity());

    assertTrue(result.startsWith("backend is unreachable"), result);
  }

  private static JsonNode parseBody(byte[] body) {
    try {
      return new ObjectMapper().readTree(body);
    } catch (IOException e) {
      throw new AssertionError("captured request body is not JSON", e);
    }
  }

  private static ToolContext contextWithIdentity() {
    return new ToolContext(Map.of(CallerContext.META_KEY, CALLER.jwt()));
  }
}
