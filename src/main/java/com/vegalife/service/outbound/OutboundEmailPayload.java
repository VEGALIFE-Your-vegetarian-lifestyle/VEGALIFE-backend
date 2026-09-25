package com.vegalife.service.outbound;

/** JSON body of an EMAIL-channel queue row; cleared once the row reaches a terminal status. */
public record OutboundEmailPayload(Type type, String username, String otp, Integer expiryMinutes) {

  public enum Type {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
  }
}
