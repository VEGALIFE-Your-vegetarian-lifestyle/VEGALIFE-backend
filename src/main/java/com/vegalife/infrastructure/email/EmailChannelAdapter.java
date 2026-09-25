package com.vegalife.infrastructure.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.service.outbound.OutboundChannelAdapter;
import com.vegalife.service.outbound.OutboundEmailPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EMAIL channel adapter: parses the queue payload and delegates to the SMTP template sender.
 * Injects {@link SmtpEmailServiceImpl} by concrete type so the queue seam never re-enters itself
 * through the {@code EmailService} interface once the outbox implements it (ADR-005).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailChannelAdapter implements OutboundChannelAdapter {

  private final SmtpEmailServiceImpl smtpEmailService;
  private final ObjectMapper objectMapper;

  @Override
  public OutboundChannel channel() {
    return OutboundChannel.EMAIL;
  }

  @Override
  public void deliver(OutboundMessage message) {
    OutboundEmailPayload payload = parse(message);
    switch (payload.type()) {
      case EMAIL_VERIFICATION ->
          smtpEmailService.sendVerificationOtp(
              message.getRecipient(), payload.username(), payload.otp());
      case PASSWORD_RESET ->
          smtpEmailService.sendPasswordResetOtp(
              message.getRecipient(), payload.username(), payload.otp());
      default ->
          throw new IllegalStateException("Unsupported outbound email type: " + payload.type());
    }
  }

  private OutboundEmailPayload parse(OutboundMessage message) {
    String json = message.getPayload();
    if (json == null || json.isBlank()) {
      throw new IllegalStateException(
          "Outbound message " + message.getId() + " has no payload to deliver");
    }
    try {
      OutboundEmailPayload payload = objectMapper.readValue(json, OutboundEmailPayload.class);
      if (payload.type() == null) {
        throw new IllegalStateException(
            "Outbound message " + message.getId() + " payload is missing a type");
      }
      return payload;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Outbound message " + message.getId() + " has an unreadable payload", e);
    }
  }
}
