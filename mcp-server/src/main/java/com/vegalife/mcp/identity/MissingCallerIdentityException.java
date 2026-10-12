package com.vegalife.mcp.identity;

/**
 * A tool was invoked without a caller identity in its context (BR-MCP-001). Tools convert this to a
 * model-readable tool result; the backend is never called unauthenticated.
 */
public class MissingCallerIdentityException extends RuntimeException {

  public MissingCallerIdentityException(String message) {
    super(message);
  }
}
