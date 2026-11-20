package com.vegalife.config;

import com.vegalife.service.ai.ToolArgumentFailureProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Replaces Spring AI's default tool-exception handling for the AI chat (issue #190).
 *
 * <p>{@code ToolCallingAutoConfiguration} declares its {@code ToolExecutionExceptionProcessor} with
 * {@code @ConditionalOnMissingBean}, so this bean is the one {@code DefaultToolCallingManager}
 * receives. The default rethrows whenever a tool failure's cause is not a {@code RuntimeException},
 * which turned a JSON argument-binding failure into a 502 for {@code create_menu}; {@link
 * ToolArgumentFailureProcessor} returns readable text instead (BR-MCP-007).
 */
@Configuration
public class ToolCallingConfig {

  @Bean
  public ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
    return new ToolArgumentFailureProcessor();
  }
}
