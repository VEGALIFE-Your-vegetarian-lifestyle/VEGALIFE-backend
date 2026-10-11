package com.vegalife.mcp.tool;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Builds an MCP tool specification that merges the incoming request's {@code _meta} into the {@link
 * ToolContext} handed to the Spring AI {@link ToolCallback}.
 *
 * <p>Spring AI's built-in conversion ({@code McpToolUtils.toSyncToolSpecification}) puts only the
 * MCP exchange into the {@code ToolContext} and never reads {@code request.meta()}, so caller
 * identity cannot reach a tool (spring-projects/spring-ai#4773, open upstream). This class is a 1:1
 * replacement for that conversion — same tool definition, same arguments serialization, same
 * text-result and error-result semantics — with the one addition of merging {@code _meta}
 * (ADR-012). The built-in converter is switched off with {@code
 * spring.ai.mcp.server.tool-callback-converter=false}; once #4773 is fixed, delete this class and
 * {@link ToolSpecificationConfig} and re-enable the converter.
 */
public final class MetaMergingToolSpecification {

  private static final Logger log = LoggerFactory.getLogger(MetaMergingToolSpecification.class);

  private MetaMergingToolSpecification() {}

  /** Wraps one Spring AI tool callback as a meta-merging MCP specification. */
  public static SyncToolSpecification from(ToolCallback toolCallback) {
    ToolDefinition definition = toolCallback.getToolDefinition();
    McpSchema.Tool tool =
        McpSchema.Tool.builder()
            .name(definition.name())
            .description(definition.description())
            .inputSchema(
                ModelOptionsUtils.jsonToObject(
                    definition.inputSchema(), McpSchema.JsonSchema.class))
            .build();

    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
        (exchange, request) -> {
          try {
            Map<String, Object> context = new HashMap<>();
            // Always present (mirrors the built-in conversion); a non-empty context is
            // also what Spring AI's MethodToolCallback requires for ToolContext params.
            context.put(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, exchange);
            if (request.meta() != null) {
              context.putAll(request.meta());
            }
            Map<String, Object> arguments =
                request.arguments() == null ? Map.of() : request.arguments();
            String converted =
                toolCallback.call(
                    ModelOptionsUtils.toJsonString(arguments), new ToolContext(context));
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(unquoteJsonString(converted))), false);
          } catch (Exception e) {
            // Tool failures are relayed as readable error results (BR-MCP-007), the same
            // way the built-in conversion treats them.
            log.warn("tool '{}' failed: {}", tool.name(), e.getMessage());
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(readableMessage(e))), true);
          }
        };

    return new SyncToolSpecification(
        tool,
        (exchange, arguments) ->
            handler.apply(exchange, new McpSchema.CallToolRequest(tool.name(), arguments)),
        handler);
  }

  /**
   * {@code DefaultToolCallResultConverter} JSON-encodes String results ({@code text} becomes {@code
   * "text"}), which would send every backend envelope to the model as an escaped, quoted blob.
   * Unwrap a JSON string literal once so tool results reach the model exactly as documented in
   * {@code docs/apis/mcp/post-mcp.md}. Already-JSON payloads (starting with {@code {} or [}) pass
   * through untouched.
   */
  private static String unquoteJsonString(String converted) {
    if (converted == null || !converted.startsWith("\"")) {
      return converted;
    }
    try {
      return ModelOptionsUtils.jsonToObject(converted, String.class);
    } catch (RuntimeException notJsonString) {
      return converted;
    }
  }

  private static String readableMessage(Exception e) {
    String message = e.getMessage();
    return (message == null || message.isBlank()) ? e.getClass().getSimpleName() : message;
  }
}
