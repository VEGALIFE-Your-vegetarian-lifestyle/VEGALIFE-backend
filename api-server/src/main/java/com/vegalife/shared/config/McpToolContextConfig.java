package com.vegalife.shared.config;

import com.vegalife.service.ai.AiToolContext;
import java.util.Map;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP client security wiring for the AI chat (ADR-012). Spring AI's default {@link
 * ToolContextToMcpMetaConverter} forwards every {@code ToolContext} entry into the MCP request
 * {@code _meta}; this bean allowlists the caller-identity key only, so nothing else can travel to
 * the MCP server and the JWT crosses process boundaries solely as the one identity token.
 */
@Configuration
public class McpToolContextConfig {

  @Bean
  ToolContextToMcpMetaConverter callerJwtAllowlistingMetaConverter() {
    return toolContext -> {
      Object jwt =
          toolContext == null ? null : toolContext.getContext().get(AiToolContext.CALLER_JWT_KEY);
      return jwt == null ? Map.of() : Map.of(AiToolContext.CALLER_JWT_KEY, jwt);
    };
  }
}
