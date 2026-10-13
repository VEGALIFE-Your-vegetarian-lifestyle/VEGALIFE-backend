package com.vegalife.service.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-request tool context for the AI chat.
 *
 * <p>Two keys are present while the move to in-process tools (ADR-013) is in flight:
 *
 * <ul>
 *   <li>{@link #CALLER_JWT_KEY} — the caller's JWT, consumed by the Spring AI MCP client and its
 *       allowlisting meta converter (see {@code McpToolContextConfig}), which forwards only this
 *       key into the MCP request {@code _meta} so the mcp-server can reconstruct the {@code
 *       Authorization} header for its backend REST call. Removed together with the MCP client.
 *   <li>{@link #USER_ID_KEY} — the authenticated user id, read by the in-process {@link MenuTools}.
 * </ul>
 *
 * <p>Neither key is ever a tool parameter: Spring AI ships only tool definitions across to the
 * model, and the context reaches the tool at invocation time.
 *
 * <p>The {@code CALLER_JWT_KEY} literal must match {@code CallerContext.META_KEY} on the mcp-server
 * side. The backend cannot depend on the mcp-server module (NFR-MAINT-001), so the literal is
 * duplicated here on purpose.
 */
public final class AiToolContext {

  /** Identity key shared with the mcp-server; the only entry allowed into MCP request meta. */
  public static final String CALLER_JWT_KEY = "vegalife-caller-jwt";

  /** Identity key read by the in-process menu tools; the only entry they use (BR-MCP-004). */
  public static final String USER_ID_KEY = "vegalife-caller-user-id";

  private AiToolContext() {}

  /**
   * Tool context carrying the caller's JWT, or an empty context when no token was supplied — the
   * mcp-server then refuses the tool call with a readable identity error (BR-MCP-004).
   */
  public static Map<String, Object> forCaller(String callerJwt) {
    return callerJwt == null || callerJwt.isBlank() ? Map.of() : Map.of(CALLER_JWT_KEY, callerJwt);
  }

  /**
   * Tool context carrying the authenticated caller's user id, or that key held as null when the
   * chat turn was not attributed to a user — the tools then refuse with a readable identity error
   * (BR-MCP-004) rather than acting unscoped.
   *
   * <p>The key is always present, even for a null id: Spring AI rejects a method taking a {@code
   * ToolContext} outright when it is handed an empty one, which would replace the readable refusal
   * with an {@code IllegalArgumentException} before the tool runs.
   */
  public static Map<String, Object> forUser(UUID userId) {
    Map<String, Object> context = new HashMap<>();
    context.put(USER_ID_KEY, userId);
    return context;
  }
}
