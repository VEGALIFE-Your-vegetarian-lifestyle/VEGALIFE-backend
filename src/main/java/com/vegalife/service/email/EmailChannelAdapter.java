package com.vegalife.service.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.enums.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.service.outbound.OutboundChannelAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EMAIL channel adapter: parses the queue payload and delegates to the {@link EmailService} port.
 *
 * <p>Lives beside the payload and the port rather than in {@code infrastructure/}, matching {@code
 * ContentFilterOutboundAdapter} and {@code MediaPurgeOutboundAdapter}: it touches no vendor SDK. It
 * can depend on the port rather than the SMTP class only because the queue no longer implements
 * {@code EmailService}; while the outbox was an {@code EmailService} bean, resolving this
 * dependency would have handed the drainer the enqueuer and looped row -> adapter -> new row
 * forever (ADR-005).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailChannelAdapter implements OutboundChannelAdapter {

  private final EmailService emailService;
  private final ObjectMapper objectMapper;

  @Override
  public OutboundChannel channel() {
    return OutboundChannel.EMAIL;
  }

  @Override
  public void deliver(OutboundMessage message) {
    EmailPayload payload = parse(message);
    switch (payload.type()) {
      case EMAIL_VERIFICATION ->
          emailService.sendVerificationOtp(
              message.getRecipient(), payload.displayName(), payload.otp());
      case PASSWORD_RESET ->
          emailService.sendPasswordResetOtp(
              message.getRecipient(), payload.displayName(), payload.otp());
      case RECEIPT -> {
        if (payload.receipt() == null) {
          throw new IllegalStateException(
              "Outbound message " + message.getId() + " has no receipt details to deliver");
        }
        emailService.sendPaymentReceipt(
            message.getRecipient(), payload.displayName(), payload.receipt());
      }
      default ->
          throw new IllegalStateException("Unsupported outbound email type: " + payload.type());
    }
  }

  private EmailPayload parse(OutboundMessage message) {
    String json = message.getPayload();
    if (json == null || json.isBlank()) {
      throw new IllegalStateException(
          "Outbound message " + message.getId() + " has no payload to deliver");
    }
    try {
      EmailPayload payload = objectMapper.readValue(json, EmailPayload.class);
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
