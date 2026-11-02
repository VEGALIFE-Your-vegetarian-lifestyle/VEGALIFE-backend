package com.vegalife.service.outbound;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.Instant;

/**
 * JSON body of an EMAIL-channel queue row; cleared once the row reaches a terminal status.
 *
 * <p>{@code displayName} is stored as {@code displayName}. Rows written before ADR-019 carry the
 * old {@code username} key in their stored JSON, so the alias keeps an in-flight queue drainable
 * across the deploy instead of failing every pre-existing row as unreadable.
 */
public record OutboundEmailPayload(
    Type type,
    @JsonAlias("username") String displayName,
    String otp,
    Integer expiryMinutes,
    Receipt receipt) {

  /** Back-compatible constructor for the OTP types that carry no receipt. */
  public OutboundEmailPayload(Type type, String displayName, String otp, Integer expiryMinutes) {
    this(type, displayName, otp, expiryMinutes, null);
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
