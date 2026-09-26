package com.vegalife.service.outbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.service.email.EmailService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Outbox-backed {@link EmailService} (ADR-005): enqueues an {@code outbound_message} row in the
 * caller's transaction instead of talking to SMTP, so registration and password reset succeed even
 * when email delivery is unavailable. The background drainer delivers the row afterwards.
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class OutboxEmailService implements EmailService {

  private final OutboundMessageRepository outboundMessageRepository;
  private final ObjectMapper objectMapper;

  @Value("${app.password-reset.otp-expiry-minutes:10}")
  private int otpExpiryMinutes;

  @Value("${app.email-verification.otp-expiry-minutes:10}")
  private int emailVerificationOtpExpiryMinutes;

  @Override
  public void sendVerificationOtp(String to, String username, String otp) {
    enqueue(
        OutboundEmailPayload.Type.EMAIL_VERIFICATION,
        to,
        username,
        otp,
        emailVerificationOtpExpiryMinutes);
  }

  @Override
  public void sendPasswordResetOtp(String to, String username, String otp) {
    enqueue(OutboundEmailPayload.Type.PASSWORD_RESET, to, username, otp, otpExpiryMinutes);
  }

  private void enqueue(
      OutboundEmailPayload.Type type, String to, String username, String otp, int expiryMinutes) {
    try {
      Instant now = Instant.now();
      OutboundMessage message =
          OutboundMessage.builder()
              .channel(OutboundChannel.EMAIL)
              .recipient(to)
              .payload(
                  objectMapper.writeValueAsString(
                      new OutboundEmailPayload(type, username, otp, expiryMinutes)))
              .status(OutboundStatus.PENDING)
              .attempts(0)
              .nextAttemptAt(now)
              .expiresAt(now.plusSeconds(expiryMinutes * 60L))
              .build();
      outboundMessageRepository.save(message);
      log.info("Queued {} email for {} on outbound queue", type, to);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize outbound email payload", e);
    }
  }
}
