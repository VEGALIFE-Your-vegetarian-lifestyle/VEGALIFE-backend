package com.vegalife.unit.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.vegalife.service.ai.ToolArgumentFailureProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

/**
 * Regression test for issue #190, defect A.
 *
 * <p>Spring AI's {@code DefaultToolExecutionExceptionProcessor} rethrows a {@link
 * ToolExecutionException} whenever its cause is not a {@code RuntimeException} — and Jackson's
 * {@code MismatchedInputException} extends {@code IOException}, so argument-binding failures are
 * always rethrown. That is what turned a malformed {@code create_menu} argument into a 502 instead
 * of the model-readable result BR-MCP-007 requires.
 *
 * <p>The processor must therefore never rethrow, no matter what the cause is, and must relay a
 * short readable line carrying the tool name.
 */
class ToolArgumentFailureProcessorTest {

  private static final ToolDefinition CREATE_MENU =
      DefaultToolDefinition.builder()
          .name("create_menu")
          .description("Create a drafted week")
          .inputSchema("{}")
          .build();

  private final ToolExecutionExceptionProcessor processor = new ToolArgumentFailureProcessor();

  /** The exact failure mode from the report: a checked Jackson exception as the cause. */
  @Test
  void relaysACheckedBindingFailureAsReadableText() {
    Throwable checked =
        MismatchedInputException.from(
            null,
            Object.class,
            "Cannot deserialize value of type `java.util.ArrayList<MenuTools$Day>` from String"
                + " value (token `JsonToken.VALUE_STRING`)");

    String result = processor.process(new ToolExecutionException(CREATE_MENU, checked));

    assertThat(result)
        .as("a binding failure must become tool result text, not an exception (BR-MCP-007)")
        .startsWith("error: ")
        .contains("create_menu")
        .contains("VALUE_STRING");
    assertThat(result).doesNotContain("\n").doesNotContain("\tat ");
  }

  @Test
  void relaysARuntimeCauseToo() {
    String result =
        processor.process(
            new ToolExecutionException(CREATE_MENU, new IllegalArgumentException("days is null")));

    assertThat(result).startsWith("error: ").contains("create_menu").contains("days is null");
  }

  /** A blank message must still produce a usable line rather than a dangling prefix. */
  @Test
  void fallsBackToTheCauseTypeWhenTheMessageIsBlank() {
    String result =
        processor.process(new ToolExecutionException(CREATE_MENU, new IllegalStateException("")));

    assertThat(result).startsWith("error: ").contains("create_menu").contains("IllegalState");
  }

  /** An exception with no message anywhere must still produce a usable, single-line result. */
  @Test
  void neverReturnsBlankText() {
    String result =
        processor.process(new ToolExecutionException(CREATE_MENU, new IllegalStateException()));

    assertThat(result).isNotBlank().startsWith("error: ").doesNotContain("\n");
  }
}
