package com.vegalife.mcp.client;

/**
 * The backend rejected or could not serve a tool's REST call (BR-MCP-007). Carries the HTTP status
 * and the backend's envelope message so the tool layer can relay a readable result to the model
 * instead of a stack trace. Never carries credentials.
 */
public class BackendCallException extends RuntimeException {

  private final int statusCode;

  public BackendCallException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  /** HTTP status of the backend response, or {@code 0} when the backend was unreachable. */
  public int getStatusCode() {
    return statusCode;
  }

  /** One-line, model-readable relay of the failure. */
  public String asToolMessage() {
    if (statusCode > 0) {
      return "backend call failed with HTTP " + statusCode + ": " + getMessage();
    }
    return "backend is unreachable: " + getMessage();
  }
}
