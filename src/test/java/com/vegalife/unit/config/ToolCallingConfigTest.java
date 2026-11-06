package com.vegalife.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.vegalife.config.ToolCallingConfig;
import com.vegalife.service.ai.ToolArgumentFailureProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Regression test for issue #190, defect A wiring.
 *
 * <p>The unit test on {@link ToolArgumentFailureProcessor} proves the processor behaves; this
 * proves it is the one Spring AI actually receives. {@code ToolCallingAutoConfiguration} declares
 * its own processor under {@code @ConditionalOnMissingBean}, so the whole fix hinges on {@link
 * ToolCallingConfig}'s bean being registered first — if that ever stops holding, a binding failure
 * silently returns to 502ing the chat turn.
 */
class ToolCallingConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(ToolCallingAutoConfiguration.class, ToolCallingConfig.class));

  @Test
  void replacesSpringAisDefaultProcessor() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(ToolExecutionExceptionProcessor.class);
          assertThat(context.getBean(ToolExecutionExceptionProcessor.class))
              .as("our processor must win over the framework default (issue #190)")
              .isInstanceOf(ToolArgumentFailureProcessor.class);
        });
  }

  /**
   * The behaviour that matters, end to end through the bean Spring AI would use: a checked cause —
   * exactly what a Jackson binding failure produces — must yield text, not a throw.
   */
  @Test
  void theWiredProcessorNeverRethrowsACheckedBindingFailure() {
    runner.run(
        context -> {
          ToolExecutionExceptionProcessor processor =
              context.getBean(ToolExecutionExceptionProcessor.class);

          Throwable checked =
              MismatchedInputException.from(
                  null, Object.class, "Cannot deserialize value from String value");

          String result =
              processor.process(
                  new ToolExecutionException(
                      DefaultToolDefinition.builder()
                          .name("create_menu")
                          .description("Create a drafted week")
                          .inputSchema("{}")
                          .build(),
                      checked));

          assertThat(result).startsWith("error: ").contains("create_menu");
        });
  }
}
