package com.vegalife.mcp.tool;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a meta-merging MCP tool specification for every Spring AI {@link ToolCallback} in this
 * application. The server's auto-configuration consumes {@code List<SyncToolSpecification>} beans
 * directly, so this bean replaces the built-in converter (disabled with {@code
 * spring.ai.mcp.server.tool-callback-converter=false}) — see {@link MetaMergingToolSpecification}
 * and ADR-012.
 *
 * <p>Tools may be contributed as individual {@code ToolCallback} beans or as {@code
 * List<ToolCallback>} beans; a callback reachable through both paths is registered once (by tool
 * name, first wins), the same de-duplication the built-in converter applies.
 */
@Configuration
public class ToolSpecificationConfig {

  @Bean
  List<SyncToolSpecification> menuToolSpecifications(
      ObjectProvider<ToolCallback> toolCallbacks,
      ObjectProvider<List<ToolCallback>> groupedToolCallbacks) {
    List<ToolCallback> callbacks = new ArrayList<>();
    toolCallbacks.forEach(callbacks::add);
    groupedToolCallbacks.stream().flatMap(List::stream).forEach(callbacks::add);

    Map<String, ToolCallback> byName = new LinkedHashMap<>();
    callbacks.forEach(
        callback -> byName.putIfAbsent(callback.getToolDefinition().name(), callback));

    return byName.values().stream().map(MetaMergingToolSpecification::from).toList();
  }
}
