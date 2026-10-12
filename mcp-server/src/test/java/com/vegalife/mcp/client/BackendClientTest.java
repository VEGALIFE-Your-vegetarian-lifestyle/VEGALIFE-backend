package com.vegalife.mcp.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.vegalife.mcp.identity.CallerContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * WireMock-based tests for the HTTP contract of the backend adapter (ADR-012): the caller's JWT
 * travels only as the Authorization header, request shapes match the backend's menu endpoints, and
 * failures become {@link BackendCallException}s carrying the backend's envelope message
 * (BR-MCP-007).
 */
class BackendClientTest {

  private static final CallerContext CALLER = new CallerContext("jwt-abc");

  private WireMockServer backend;
  private BackendClient client;

  @BeforeEach
  void setUp() {
    backend = new WireMockServer(wireMockConfig().dynamicPort());
    backend.start();
    client =
        new BackendClient(
            RestClient.builder().baseUrl(backend.baseUrl()).build(), new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    backend.stop();
  }

  @Test
  void getMenusSendsQueryParamsAndBearerHeader() {
    String envelope =
        "{\"success\":true,\"message\":\"Menus retrieved successfully\",\"data\":{\"content\":[]}}";
    backend.stubFor(
        get(urlPathEqualTo("/api/menus"))
            .withQueryParam("from", equalTo("2026-10-05"))
            .withQueryParam("to", equalTo("2026-10-11"))
            .withHeader("Authorization", equalTo("Bearer jwt-abc"))
            .willReturn(okJson(envelope)));

    String body = client.getMenus(CALLER, Map.of("from", "2026-10-05", "to", "2026-10-11"));

    assertEquals(envelope, body);
    // The JWT may only ever appear as the Authorization header, never in the URL.
    backend
        .getAllServeEvents()
        .forEach(e -> assertFalse(e.getRequest().getUrl().contains("jwt-abc")));
  }

  @Test
  void getMealPlanTargetsTheDetailsPath() {
    backend.stubFor(
        get(urlPathEqualTo("/api/menus/details"))
            .withQueryParam("menuId", equalTo("11111111-1111-1111-1111-111111111111"))
            .withHeader("Authorization", equalTo("Bearer jwt-abc"))
            .willReturn(okJson("{\"success\":true,\"data\":[]}")));

    String body =
        client.getMealPlan(CALLER, Map.of("menuId", "11111111-1111-1111-1111-111111111111"));

    assertEquals("{\"success\":true,\"data\":[]}", body);
  }

  @Test
  void createMenuPostsJsonBodyVerbatim() {
    backend.stubFor(
        post(urlPathEqualTo("/api/menus"))
            .withHeader("Authorization", equalTo("Bearer jwt-abc"))
            .withRequestBody(equalTo("{\"startDate\":\"2026-10-19\",\"days\":[]}"))
            .willReturn(okJson("{\"success\":true,\"data\":{\"id\":\"m-1\"}}")));

    String body = client.createMenu(CALLER, "{\"startDate\":\"2026-10-19\",\"days\":[]}");

    assertEquals("{\"success\":true,\"data\":{\"id\":\"m-1\"}}", body);
  }

  @Test
  void schedulePostsStatusBodyToTheStatusPath() {
    backend.stubFor(
        post(urlPathEqualTo("/api/menus/m-1/status"))
            .withRequestBody(equalTo("{\"status\":\"scheduled\"}"))
            .willReturn(okJson("{\"success\":true}")));

    assertEquals(
        "{\"success\":true}", client.updateMenuStatus(CALLER, "m-1", "{\"status\":\"scheduled\"}"));
  }

  @Test
  void errorEnvelopeMessageIsSurfacedInTheException() {
    backend.stubFor(
        post(urlPathEqualTo("/api/menus"))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":false,\"message\":\"Menu overlaps an existing"
                            + " week\",\"data\":null}")));

    BackendCallException ex =
        assertThrows(
            BackendCallException.class,
            () -> client.createMenu(CALLER, "{\"startDate\":\"2026-10-19\",\"days\":[]}"));

    assertEquals(409, ex.getStatusCode());
    assertEquals("Menu overlaps an existing week", ex.getMessage());
    assertEquals(
        "backend call failed with HTTP 409: Menu overlaps an existing week", ex.asToolMessage());
  }

  @Test
  void nonJsonErrorBodyFallsBackToHttpStatusText() {
    backend.stubFor(
        get(urlPathEqualTo("/api/menus"))
            .willReturn(aResponse().withStatus(502).withBody("<html>bad gateway</html>")));

    BackendCallException ex =
        assertThrows(BackendCallException.class, () -> client.getMenus(CALLER, Map.of()));

    assertEquals(502, ex.getStatusCode());
    assertEquals("Bad Gateway", ex.getMessage());
  }

  @Test
  void unreachableBackendMapsToStatusZero() {
    backend.stop();

    BackendCallException ex =
        assertThrows(BackendCallException.class, () -> client.getMenus(CALLER, Map.of()));

    assertEquals(0, ex.getStatusCode());
    assertEquals("could not reach the backend", ex.getMessage());
  }

  @Test
  void droppedConnectionMapsToUnreachable() {
    backend.stubFor(
        get(urlPathEqualTo("/api/menus"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    BackendCallException ex =
        assertThrows(BackendCallException.class, () -> client.getMenus(CALLER, Map.of()));

    assertEquals(0, ex.getStatusCode());
  }
}
