package com.vegalife.service.ai;

import java.lang.reflect.Type;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.util.json.JsonParser;

/**
 * Lets a tool method decide its own result shape, without the framework JSON-encoding a String a
 * second time (BR-MCP-007).
 *
 * <p>{@link org.springframework.ai.tool.execution.DefaultToolCallResultConverter} runs every
 * non-void result through {@link JsonParser#toJson}, so a method returning the documented {@code
 * {"success":true,...}} envelope as a {@link String} would hand the model {@code
 * "{\"success\":true,...}"} — an escaped, quoted blob. The menu tools return their payload already
 * serialized, and their failures as plain readable text, so this converter passes a {@link String}
 * through untouched and only JSON-encodes anything else.
 *
 * <p>This is the same responsibility {@code unquoteJsonString} carried while the tools were served
 * over MCP, moved from the transport boundary onto the one attribute the framework provides for it.
 * Referenced from each {@code @Tool} via {@code resultConverter =
 * RawTextToolResultConverter.class}.
 */
public final class RawTextToolResultConverter implements ToolCallResultConverter {

  @Override
  public String convert(Object result, Type returnType) {
    if (result instanceof String text) {
      return text;
    }
    return JsonParser.toJson(result);
  }
}
