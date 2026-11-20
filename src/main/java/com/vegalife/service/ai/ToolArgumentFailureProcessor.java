package com.vegalife.service.ai;

import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

/**
 * Keeps a tool-argument failure inside the conversation (issue #190, BR-MCP-007).
 *
 * <p>Spring AI binds a tool call's arguments before invoking the method, and its default {@link
 * ToolExecutionExceptionProcessor} rethrows whenever the failure's cause is not a {@code
 * RuntimeException}. Jackson's {@code MismatchedInputException} extends {@code IOException}, so a
 * provider that emits a structured parameter in the wrong JSON shape (for example {@code days} as a
 * quoted string instead of an array) always rethrown — past {@code MenuTools}' own catch, out of
 * {@code OpenAiChatModel}, and into the chat endpoint as a 502. The member saw a provider outage;
 * the model never saw the error it could have corrected.
 *
 * <p>This processor never rethrows. A binding failure becomes a single readable {@code error: ...}
 * line — the same shape {@code MenuTools.toolFailure} produces — so the assistant can retry the
 * call with a corrected shape within the same turn. Only the tool name and the cause's message are
 * relayed: argument values and the raw source excerpt stay out of the text (BR-MCP-004), and
 * Jackson's source location is already redacted.
 *
 * <p>Registered as a bean in {@code config/}, which replaces Spring AI's default through
 * {@code @ConditionalOnMissingBean}.
 */
public class ToolArgumentFailureProcessor implements ToolExecutionExceptionProcessor {

  private static final String PREFIX = "error: ";

  /**
   * @param exception the bind-or-invoke failure Spring AI raised
   * @return a single-line, model-readable result; never null, never blank
   */
  @Override
  public String process(ToolExecutionException exception) {
    String tool = toolName(exception);
    String detail = describe(exception.getCause());
    return PREFIX
        + tool
        + " could not read its arguments: "
        + detail
        + " (call it again with arguments matching its schema)";
  }

  /** The tool whose arguments failed, or a neutral label if Spring AI did not carry one. */
  private static String toolName(ToolExecutionException exception) {
    ToolDefinition definition = exception.getToolDefinition();
    if (definition == null || definition.name() == null || definition.name().isBlank()) {
      return "the tool";
    }
    return "tool '" + definition.name() + "'";
  }

  /**
   * One line describing the cause. Jackson messages span lines and embed a source excerpt, which
   * would flood the model's context, so the message is collapsed to its first non-blank line.
   */
  private static String describe(Throwable cause) {
    if (cause == null) {
      return "the arguments did not match its schema";
    }
    String message = cause.getMessage();
    if (message == null || message.isBlank()) {
      return cause.getClass().getSimpleName();
    }
    for (String line : message.split("\\R")) {
      String trimmed = line.strip();
      if (!trimmed.isEmpty()) {
        return trimmed;
      }
    }
    return cause.getClass().getSimpleName();
  }
}
