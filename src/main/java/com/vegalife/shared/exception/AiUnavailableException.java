package com.vegalife.shared.exception;

/** Raised when the {@code ai_chat_enabled} platform setting is off (issue #13 follow-up). */
public class AiUnavailableException extends RuntimeException {

  public AiUnavailableException(String message) {
    super(message);
  }
}
