package com.vegalife.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.mcp.client.BackendClient;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the menu tools' Spring AI callbacks; {@link ToolSpecificationConfig} turns every such
 * callback into a meta-merging MCP tool specification.
 */
@Configuration
public class MenuToolsConfig {

  @Bean
  MenuTools menuTools(BackendClient backendClient, ObjectMapper objectMapper) {
    return new MenuTools(backendClient, objectMapper);
  }

  @Bean
  List<ToolCallback> menuToolCallbacks(MenuTools menuTools) {
    return List.of(
        MethodToolCallbackProvider.builder().toolObjects(menuTools).build().getToolCallbacks());
  }
}
