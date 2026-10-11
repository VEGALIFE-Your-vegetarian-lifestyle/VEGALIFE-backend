package com.vegalife.mcp.identity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.ai.chat.model.ToolContext;

/**
 * The authenticated member on whose behalf a tool call executes (BR-MCP-001).
 *
 * <p>The identity travels server-side only: the backend chat endpoint places the caller's JWT in
 * the Spring AI {@link ToolContext}, the client serializes it into the MCP request {@code _meta},
 * and {@code MetaMergingToolSpecification} merges {@code _meta} back into the tool-side {@link
 * ToolContext} (ADR-012). It is never a tool parameter and never model-visible.
 */
public record CallerContext(String jwt) {

  /** {@code _meta} / {@link ToolContext} key carrying the caller's JWT. */
  public static final String META_KEY = "vegalife-caller-jwt";

  public CallerContext {
    Objects.requireNonNull(jwt, "jwt");
    if (jwt.isBlank()) {
      throw new IllegalArgumentException("caller jwt must not be blank");
    }
  }

  /** Reads the caller identity from a tool invocation context. */
  public static Optional<CallerContext> from(ToolContext toolContext) {
    if (toolContext == null || toolContext.getContext() == null) {
      return Optional.empty();
    }
    return optionalFromMeta(toolContext.getContext());
  }

  /** Reads the caller identity from a raw {@code _meta}-style map. */
  public static Optional<CallerContext> fromMeta(Map<String, Object> meta) {
    if (meta == null) {
      return Optional.empty();
    }
    return optionalFromMeta(meta);
  }

  /** The caller identity or a model-readable failure when it is missing. */
  public static CallerContext require(ToolContext toolContext) {
    return from(toolContext)
        .orElseThrow(
            () -> new MissingCallerIdentityException("no caller identity on this tool invocation"));
  }

  /** Value for the backend call's {@code Authorization} header. */
  public String authorizationHeader() {
    return "Bearer " + jwt;
  }

  private static Optional<CallerContext> optionalFromMeta(Map<String, Object> meta) {
    Object value = meta.get(META_KEY);
    if (value instanceof String jwt && !jwt.isBlank()) {
      return Optional.of(new CallerContext(jwt));
    }
    return Optional.empty();
  }
}
