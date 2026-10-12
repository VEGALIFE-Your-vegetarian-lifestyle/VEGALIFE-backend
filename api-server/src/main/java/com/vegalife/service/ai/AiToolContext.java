package com.vegalife.service.ai;

import java.util.Map;

/**
 * Per-request tool context for the AI chat (ADR-012). Carries the caller's JWT from the chat
 * endpoint to the Spring AI MCP client, whose allowlisting meta converter (see {@code
 * McpToolContextConfig}) forwards only this key into the MCP request {@code _meta}; the mcp-server
 * reconstructs the {@code Authorization} header from it for the backend REST call.
 *
 * <p>The key literal must match {@code CallerContext.META_KEY} on the mcp-server side. The backend
 * cannot depend on the mcp-server module (NFR-MAINT-001), so the literal is duplicated here on
 * purpose.
 */
public final class AiToolContext {

  /** Identity key shared with the mcp-server; the only entry allowed into MCP request meta. */
  public static final String CALLER_JWT_KEY = "vegalife-caller-jwt";

  private AiToolContext() {}

  /**
   * Tool context carrying the caller's JWT, or an empty context when no token was supplied — the
   * mcp-server then refuses the tool call with a readable identity error (BR-MCP-004).
   */
  public static Map<String, Object> forCaller(String callerJwt) {
    return callerJwt == null || callerJwt.isBlank() ? Map.of() : Map.of(CALLER_JWT_KEY, callerJwt);
  }
}
