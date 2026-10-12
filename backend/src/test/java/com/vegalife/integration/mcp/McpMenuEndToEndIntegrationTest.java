package com.vegalife.integration.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Both-servers end-to-end test (ADR-012): boots postgres + mcp-server + backend in Docker through
 * {@code docker-compose.it.yml}, registers a member through the backend REST API, and then drives
 * the MCP server's {@code /mcp} endpoint with that member's real JWT. The identity chain proven
 * here is the same one the AI chat uses: caller JWT in the request {@code _meta}, forwarded by the
 * MCP server as the Authorization header of the backend REST call.
 */
class McpMenuEndToEndIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String COMPOSE_FILE = "src/test/resources/docker-compose.it.yml";
  private static final String BACKEND = "http://localhost:18080";
  private static final String MCP = "http://localhost:18081/mcp";
  private static final String PG_JDBC = "jdbc:postgresql://localhost:15433/vegalife";

  /** SHA-256 of the OTP code {@code 123456}; lets the test verify email without an SMTP catcher. */
  private static final String OTP_HASH =
      "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92";

  private static final List<String> DISH_IDS =
      List.of(
          "aaaaaaa1-0000-4000-8000-000000000001",
          "aaaaaaa1-0000-4000-8000-000000000002",
          "aaaaaaa1-0000-4000-8000-000000000003",
          "aaaaaaa1-0000-4000-8000-000000000004",
          "aaaaaaa1-0000-4000-8000-000000000005");

  private static String jwt;
  private static String userId;
  private static Connection db;

  @BeforeAll
  static void stackUp() throws Exception {
    compose("up", "-d", "--build", "--wait", "--wait-timeout", "600");
    waitForBackendAndMcp();

    db = openDatabaseWithRetry();
    registerVerifyLogin();
    seedDishes();
  }

  @AfterAll
  static void stackDown() throws Exception {
    if (db != null) {
      try {
        db.close();
      } catch (SQLException ignored) {
        // teardown continues regardless
      }
    }
    compose("down", "-v", "--remove-orphans");
  }

  @Test
  void createMenuThroughMcpPersistsDraftForCaller() throws Exception {
    String session = mcpSession();
    String week = "2026-10-19";

    JsonNode created = mcpToolResult(session, "create_menu", weekArguments(week), jwt);

    assertTrue(created.path("success").asBoolean(), created.toString());
    String menuId = created.path("data").path("id").asText();
    assertTrue(!menuId.isBlank(), "created menu must carry an id");

    assertEquals(
        1,
        scalar(
            "SELECT count(*) FROM menu WHERE user_id='"
                + userId
                + "' AND start_date='"
                + week
                + "' AND status='drafted'"),
        "the drafted menu must be persisted in Postgres for the caller");

    // Deep-mode read with the same identity returns the persisted menu's detail.
    JsonNode readBack = mcpToolResult(session, "get_menu", "{\"week\":\"" + week + "\"}", jwt);
    assertTrue(readBack.toString().contains(menuId), readBack.toString());
  }

  @Test
  void menuToolsRefuseToReachTheBackendWithoutCallerIdentity() throws Exception {
    String session = mcpSession();
    String week = "2026-11-02";

    String raw = mcpCall(session, toolsCallBody("create_menu", weekArguments(week), null));

    assertTrue(raw.contains("caller identity missing"), raw);
    assertEquals(
        0,
        scalar(
            "SELECT count(*) FROM menu WHERE user_id='"
                + userId
                + "' AND start_date='"
                + week
                + "'"),
        "no menu may be created without the caller's identity");
  }

  // ---------------------------------------------------------------- stack plumbing

  private static void compose(String... args) throws IOException, InterruptedException {
    String[] command = new String[args.length + 4];
    command[0] = "docker";
    command[1] = "compose";
    command[2] = "-f";
    command[3] = Path.of(COMPOSE_FILE).toAbsolutePath().toString();
    System.arraycopy(args, 0, command, 4, args.length);

    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (!process.waitFor(30, TimeUnit.MINUTES)) {
      process.destroyForcibly();
      throw new AssertionError("docker compose " + String.join(" ", args) + " timed out");
    }
    if (process.exitValue() != 0) {
      throw new AssertionError("docker compose " + String.join(" ", args) + " failed:\n" + output);
    }
  }

  private static void waitForBackendAndMcp() {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
    while (System.nanoTime() < deadline) {
      if (isHealthy(BACKEND) && isHealthy("http://localhost:18081")) {
        return;
      }
      sleep(2000);
    }
    throw new AssertionError("backend or mcp-server did not become healthy on the host ports");
  }

  private static boolean isHealthy(String base) {
    try {
      HttpResponse<String> response =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(URI.create(base + "/actuator/health")).GET().build(),
                  HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  private static Connection openDatabaseWithRetry() throws SQLException {
    SQLException last = null;
    for (int i = 0; i < 30; i++) {
      try {
        return DriverManager.getConnection(PG_JDBC, "vegalife", "vegalife");
      } catch (SQLException notUpYet) {
        last = notUpYet;
        sleep(2000);
      }
    }
    throw last;
  }

  // ---------------------------------------------------------------- auth + seeding

  private static void registerVerifyLogin() throws Exception {
    HttpResponse<String> register =
        http(
            "POST",
            BACKEND + "/api/auth/register",
            """
            {"username":"mcpe2e","email":"mcpe2e@example.test","password":"SpikePass123",\
            "confirmPassword":"SpikePass123"}""");
    assertTrue(register.body().contains("\"success\":true"), register.body());

    userId =
        text(
            "SELECT user_id FROM otp_code WHERE purpose='EMAIL_VERIFICATION'"
                + " ORDER BY created_at DESC LIMIT 1");
    execute(
        "UPDATE otp_code SET otp_hash='"
            + OTP_HASH
            + "' WHERE purpose='EMAIL_VERIFICATION' AND user_id='"
            + userId
            + "'");

    HttpResponse<String> verify =
        http(
            "POST",
            BACKEND + "/api/auth/verify-email",
            """
            {"email":"mcpe2e@example.test","otp":"123456"}""");
    assertTrue(verify.body().contains("\"success\":true"), verify.body());

    HttpResponse<String> login =
        http(
            "POST",
            BACKEND + "/api/auth/login",
            """
            {"identifier":"mcpe2e@example.test","password":"SpikePass123"}""");
    jwt = MAPPER.readTree(login.body()).path("data").path("accessToken").asText();
    assertTrue(!jwt.isBlank(), "login must return an access token: " + login.body());
  }

  private static void seedDishes() throws SQLException {
    for (int i = 0; i < DISH_IDS.size(); i++) {
      execute(
          "INSERT INTO dish (id, name) VALUES ('"
              + DISH_IDS.get(i)
              + "', 'E2E Dish "
              + (i + 1)
              + "')");
    }
  }

  // ---------------------------------------------------------------- MCP protocol

  private static String mcpSession() throws Exception {
    HttpResponse<String> init =
        http(
            "POST",
            MCP,
            """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{\
            "protocolVersion":"2025-06-18","capabilities":{},\
            "clientInfo":{"name":"mcp-e2e","version":"1.0"}}}""",
            true);
    String session = init.headers().firstValue("mcp-session-id").orElse(null);
    assertTrue(session != null && !session.isBlank(), "initialize must yield a session id");

    http(
        "POST",
        MCP,
        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
        true,
        Map.of("Mcp-Session-Id", session));
    return session;
  }

  private static JsonNode mcpToolResult(String session, String tool, String arguments, String jwt)
      throws Exception {
    String raw = mcpCall(session, toolsCallBody(tool, arguments, jwt));
    String json = lastDataLine(raw);
    JsonNode content = MAPPER.readTree(json).path("result").path("content");
    if (!content.isArray() || content.size() == 0) {
      throw new AssertionError("tool " + tool + " returned no text content: " + raw);
    }
    return MAPPER.readTree(content.get(0).path("text").asText());
  }

  private static String mcpCall(String session, String body) throws Exception {
    return http("POST", MCP, body, true, Map.of("Mcp-Session-Id", session)).body();
  }

  private static String toolsCallBody(String tool, String arguments, String jwt) {
    String meta = jwt == null ? "" : ",\"_meta\":{\"vegalife-caller-jwt\":\"" + jwt + "\"}";
    return "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\""
        + tool
        + "\",\"arguments\":"
        + arguments
        + meta
        + "}}";
  }

  /** The JSON-RPC payload of an SSE or plain-JSON MCP response. */
  private static String lastDataLine(String body) {
    String json = body;
    for (String line : body.split("\r?\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("data:")) {
        json = trimmed.substring("data:".length()).trim();
      }
    }
    return json;
  }

  /** Seven days of one breakfast each from the seeded dishes (BR-MENU-005 satisfied). */
  private static String weekArguments(String startDate) {
    StringBuilder days = new StringBuilder("[");
    java.time.LocalDate start = java.time.LocalDate.parse(startDate);
    for (int d = 0; d < 7; d++) {
      if (d > 0) {
        days.append(",");
      }
      days.append("{\"date\":\"")
          .append(start.plusDays(d))
          .append("\",\"meals\":[{\"mealType\":\"BREAKFAST\",\"dishId\":\"")
          .append(DISH_IDS.get(d < 5 ? d : d - 5))
          .append("\",\"servings\":2}]}");
    }
    return "{\"startDate\":\"" + startDate + "\",\"days\":" + days.append("]") + "}";
  }

  // ---------------------------------------------------------------- HTTP + JDBC helpers

  private static HttpResponse<String> http(String method, String url, String body)
      throws Exception {
    return http(method, url, body, false);
  }

  private static HttpResponse<String> http(String method, String url, String body, boolean mcp)
      throws Exception {
    return http(method, url, body, mcp, Map.of());
  }

  private static HttpResponse<String> http(
      String method, String url, String body, boolean mcp, Map<String, String> headers)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .method(method, HttpRequest.BodyPublishers.ofString(body));
    builder.header("Content-Type", "application/json");
    if (mcp) {
      builder.header("Accept", "application/json, text/event-stream");
    }
    headers.forEach(builder::header);
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static long scalar(String sql) throws SQLException {
    try (Statement statement = db.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static String text(String sql) throws SQLException {
    try (Statement statement = db.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      rs.next();
      return rs.getString(1);
    }
  }

  private static void execute(String sql) throws SQLException {
    try (Statement statement = db.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted while waiting", e);
    }
  }
}
