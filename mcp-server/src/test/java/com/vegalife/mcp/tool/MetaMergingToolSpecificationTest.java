package com.vegalife.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vegalife.mcp.identity.CallerContext;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/**
 * Proves the fix for spring-projects/spring-ai#4773 on the server side: request {@code _meta}
 * reaches the tool's {@link ToolContext}, and error semantics match the built-in conversion.
 */
class MetaMergingToolSpecificationTest {

  /** Public nested class: Spring AI invokes @Tool methods reflectively. */
  public static class ProbeTool {

    ToolContext capturedContext;

    @Tool(name = "probe", description = "captures its tool context")
    public String probe(String text, ToolContext toolContext) {
      this.capturedContext = toolContext;
      return "echo:" + text;
    }
  }

  public static class FailingTool {

    @Tool(name = "boom", description = "always fails")
    public String boom() {
      throw new IllegalStateException("kaputt");
    }
  }

  @Test
  void mergesRequestMetaIntoToolContext() {
    ProbeTool tool = new ProbeTool();
    SyncToolSpecification spec = specFor(tool);

    McpSchema.CallToolResult result =
        spec.callHandler()
            .apply(
                null,
                new McpSchema.CallToolRequest(
                    "probe", Map.of("text", "hi"), Map.of(CallerContext.META_KEY, "jwt-token")));

    assertFalse(Boolean.TRUE.equals(result.isError()));
    assertEquals("echo:hi", firstText(result));
    assertEquals("jwt-token", tool.capturedContext.getContext().get(CallerContext.META_KEY));
    assertEquals("jwt-token", CallerContext.require(tool.capturedContext).jwt());
  }

  @Test
  void noMetaYieldsNoCallerIdentity() {
    ProbeTool tool = new ProbeTool();
    SyncToolSpecification spec = specFor(tool);

    McpSchema.CallToolResult result =
        spec.callHandler()
            .apply(null, new McpSchema.CallToolRequest("probe", Map.of("text", "hi")));

    assertFalse(Boolean.TRUE.equals(result.isError()));
    assertTrue(CallerContext.from(tool.capturedContext).isEmpty());
  }

  @Test
  void toolFailureBecomesReadableErrorResult() {
    SyncToolSpecification spec = specFor(new FailingTool());

    McpSchema.CallToolResult result =
        spec.callHandler().apply(null, new McpSchema.CallToolRequest("boom", Map.of()));

    assertTrue(Boolean.TRUE.equals(result.isError()));
    assertEquals("kaputt", firstText(result));
  }

  @Test
  void jsonPayloadToolResultIsUnquoted() {
    SyncToolSpecification spec = specFor(new PayloadTool());

    McpSchema.CallToolResult result =
        spec.callHandler().apply(null, new McpSchema.CallToolRequest("payload", Map.of()));

    assertFalse(Boolean.TRUE.equals(result.isError()));
    assertEquals("{\"success\":true}", firstText(result));
  }

  /** Returns a raw backend-style JSON payload as a String. */
  public static class PayloadTool {

    @Tool(name = "payload", description = "returns a JSON payload")
    public String payload() {
      return "{\"success\":true}";
    }
  }

  private static SyncToolSpecification specFor(Object toolObject) {
    ToolCallback callback =
        MethodToolCallbackProvider.builder().toolObjects(toolObject).build().getToolCallbacks()[0];
    return MetaMergingToolSpecification.from(callback);
  }

  private static String firstText(McpSchema.CallToolResult result) {
    return ((McpSchema.TextContent) result.content().getFirst()).text();
  }
}
