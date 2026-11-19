package com.vegalife.service.email;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.Instant;

/**
 * JSON body of an EMAIL-channel queue row.
 *
 * <p>{@code displayName} is stored as {@code displayName}. Rows written before ADR-019 carry the
 * old {@code username} key in their stored JSON, so the alias keeps an in-flight queue drainable
 * across the deploy instead of failing every pre-existing row as unreadable.
 *
 * <p>The payload carries the plaintext OTP because {@code otp_code} stores only its SHA-256 hash
 * (BR-AUTH-019), so the drainer could not recover a code from the row. That is also why this type
 * lives here rather than in {@code infrastructure/}: the services that enqueue are rank 5, and a
 * rank-6 home would be an upward dependency.
 */
public record EmailPayload(
    Type type,
    @JsonAlias("username") String displayName,
    String otp,
    Integer expiryMinutes,
    Receipt receipt) {

  /** Back-compatible constructor for the OTP types that carry no receipt. */
  public EmailPayload(Type type, String displayName, String otp, Integer expiryMinutes) {
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
