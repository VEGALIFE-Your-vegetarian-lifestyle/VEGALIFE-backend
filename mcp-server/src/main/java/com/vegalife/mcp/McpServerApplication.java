package com.vegalife.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP server application exposing the Vegalife menu tools over Streamable HTTP at {@code /mcp}.
 *
 * <p>Separate deployable on an internal network (ADR-012): a thin adapter that translates tool
 * calls into authenticated REST calls against the backend. It contains no business rules and no
 * data access.
 */
@SpringBootApplication
public class McpServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(McpServerApplication.class, args);
  }
}
