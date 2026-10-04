package com.vegalife.service.outbound;

import java.time.Instant;

/** JSON body of an EMAIL-channel queue row; cleared once the row reaches a terminal status. */
public record OutboundEmailPayload(
    Type type, String username, String otp, Integer expiryMinutes, Receipt receipt) {

  /** Back-compatible constructor for the OTP types that carry no receipt. */
  public OutboundEmailPayload(Type type, String username, String otp, Integer expiryMinutes) {
    this(type, username, otp, expiryMinutes, null);
  }

  /** Purchase receipt details (BR-PAY-007); only present when {@code type == RECEIPT}. */
  public record Receipt(
      String planName, long amount, String currency, Instant paidAt, String reference) {}

  public enum Type {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    RECEIPT
  }
}
