package com.vegalife.mcp.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.mcp.identity.CallerContext;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin adapter over the backend's menu REST endpoints (ADR-012): every call forwards the caller's
 * JWT as the {@code Authorization} header and returns the backend's JSON response body verbatim.
 * The backend stays the single source of business rules; this class adds no behavior beyond
 * timeouts and error translation into {@link BackendCallException}.
 *
 * <p>The JWT is used to build the header only — it is never logged, never put in a URL, and never
 * stored on the client.
 */
public class BackendClient {

  private static final Logger log = LoggerFactory.getLogger(BackendClient.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public BackendClient(RestClient restClient, ObjectMapper objectMapper) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
  }

  /** {@code GET /api/menus} with the given (period/date/status/month/range) query parameters. */
  public String getMenus(CallerContext caller, Map<String, String> query) {
    return execute(
        () ->
            restClient
                .get()
                .uri(
                    uriBuilder -> {
                      uriBuilder.path("/api/menus");
                      query.forEach(uriBuilder::queryParam);
                      return uriBuilder.build();
                    })
                .headers(headers -> headers.setBearerAuth(caller.jwt()))
                .retrieve()
                .body(String.class),
        "list menus");
  }

  /**
   * {@code GET /api/menus/details} with the given (from/to/menuId) query parameters. Unlike the
   * list, this returns every matching menu with its full day-by-day meal detail, so the backend
   * does the assembling rather than the caller making one call per menu.
   */
  public String getMealPlan(CallerContext caller, Map<String, String> query) {
    return execute(
        () ->
            restClient
                .get()
                .uri(
                    uriBuilder -> {
                      uriBuilder.path("/api/menus/details");
                      query.forEach(uriBuilder::queryParam);
                      return uriBuilder.build();
                    })
                .headers(headers -> headers.setBearerAuth(caller.jwt()))
                .retrieve()
                .body(String.class),
        "get meal plan");
  }

  /** {@code POST /api/menus} with a JSON request body. */
  public String createMenu(CallerContext caller, String jsonBody) {
    return execute(
        () ->
            restClient
                .post()
                .uri("/api/menus")
                .headers(headers -> headers.setBearerAuth(caller.jwt()))
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .retrieve()
                .body(String.class),
        "create menu");
  }

  /** {@code PATCH /api/menus/{menuId}} with a JSON request body (replaces {@code days}). */
  public String updateMenu(CallerContext caller, String menuId, String jsonBody) {
    return execute(
        () ->
            restClient
                .patch()
                .uri("/api/menus/{menuId}", menuId)
                .headers(headers -> headers.setBearerAuth(caller.jwt()))
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .retrieve()
                .body(String.class),
        "update menu " + menuId);
  }

  /** {@code POST /api/menus/{menuId}/status} with a JSON body such as {@code {"status":...}}. */
  public String updateMenuStatus(CallerContext caller, String menuId, String jsonBody) {
    return execute(
        () ->
            restClient
                .post()
                .uri("/api/menus/{menuId}/status", menuId)
                .headers(headers -> headers.setBearerAuth(caller.jwt()))
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .retrieve()
                .body(String.class),
        "update menu status " + menuId);
  }

  /**
   * Runs one REST call: logs only the operation and status (never headers or bodies with
   * credentials) and converts failures into {@link BackendCallException} carrying the backend's
   * envelope message.
   */
  private String execute(Supplier<String> call, String operation) {
    try {
      String body = call.get();
      log.debug("backend op '{}' succeeded", operation);
      return body;
    } catch (RestClientResponseException e) {
      log.debug("backend op '{}' failed with status {}", operation, e.getStatusCode().value());
      throw new BackendCallException(
          e.getStatusCode().value(), messageFrom(e.getResponseBodyAsString(), e.getStatusText()));
    } catch (RestClientException e) {
      log.warn("backend op '{}' unreachable: {}", operation, e.toString());
      throw new BackendCallException(0, "could not reach the backend");
    }
  }

  /** Extracts the {@code message} field of the {@code {success, message, data}} envelope. */
  private String messageFrom(String body, String fallback) {
    if (body == null || body.isBlank()) {
      return fallback;
    }
    try {
      JsonNode node = objectMapper.readTree(body);
      if (node != null && node.hasNonNull("message")) {
        return node.get("message").asText();
      }
    } catch (JsonProcessingException notJson) {
      // HTML/plain error page — fall back to the HTTP status text
      log.debug("backend error body is not a JSON envelope");
    }
    return fallback;
  }
}
