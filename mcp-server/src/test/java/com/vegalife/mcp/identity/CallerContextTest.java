package com.vegalife.mcp.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class CallerContextTest {

  @Test
  void readsJwtFromToolContext() {
    ToolContext toolContext = new ToolContext(Map.of(CallerContext.META_KEY, "token-abc"));

    CallerContext caller = CallerContext.require(toolContext);

    assertEquals("token-abc", caller.jwt());
    assertEquals("Bearer token-abc", caller.authorizationHeader());
  }

  @Test
  void missingIdentityYieldsEmptyOptionalAndThrowsOnRequire() {
    ToolContext toolContext = new ToolContext(Map.of("unrelated", "value"));

    assertTrue(CallerContext.from(toolContext).isEmpty());
    assertThrows(MissingCallerIdentityException.class, () -> CallerContext.require(toolContext));
  }

  @Test
  void blankJwtIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new CallerContext("  "));
  }
}
