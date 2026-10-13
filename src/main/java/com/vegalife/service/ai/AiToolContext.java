package com.vegalife.service.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-request tool context for the AI chat (ADR-013). Carries the caller's authenticated user id
 * from the chat endpoint to the in-process {@code @Tool} methods, which use it to scope every menu
 * read and write to the owner (BR-MCP-004).
 *
 * <p>Spring AI ships only tool definitions across to the model, and the context reaches the tool at
 * invocation time, so the user id is never a tool parameter and never model-visible. It is
 * server-derived: {@code JwtAuthenticationFilter} populates the principal only after validating the
 * token, so nothing here is caller-supplied input.
 */
public final class AiToolContext {

  /** Identity key read by the in-process menu tools; the only entry they use (BR-MCP-004). */
  public static final String USER_ID_KEY = "vegalife-caller-user-id";

  private AiToolContext() {}

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
