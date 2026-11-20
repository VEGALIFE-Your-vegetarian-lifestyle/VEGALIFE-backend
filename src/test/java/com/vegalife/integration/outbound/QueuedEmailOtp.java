package com.vegalife.integration.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.enums.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.service.email.EmailPayload;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the OTP a flow wrote to the outbound queue. Before the enqueue was inlined, tests captured
 * the code from an {@code EmailService} mock; the queue row is now the only place the plaintext
 * code exists, so this helper resolves it by recipient and payload type.
 *
 * <p>Only usable where scheduling is off ({@code app.scheduling.enabled=false}), since the drainer
 * would otherwise consume the pending row before it could be read. The integration profile sets
 * that.
 */
public final class QueuedEmailOtp {

  private QueuedEmailOtp() {}

  /** Latest queued OTP for {@code recipient} of the given type; fails if none is readable. */
  public static String latest(
      OutboundMessageRepository repository,
      ObjectMapper objectMapper,
      String recipient,
      EmailPayload.Type type) {
    List<String> otps = all(repository, objectMapper, recipient, type);
    if (otps.isEmpty()) {
      throw new AssertionError(
          "No queued " + type + " OTP for " + recipient + " on the outbound queue");
    }
    return otps.get(otps.size() - 1);
  }

  /** Every queued OTP for {@code recipient} of the given type, oldest first. */
  public static List<String> all(
      OutboundMessageRepository repository,
      ObjectMapper objectMapper,
      String recipient,
      EmailPayload.Type type) {
    return repository.findAll().stream()
        .filter(row -> row.getChannel() == OutboundChannel.EMAIL)
        .filter(row -> recipient.equals(row.getRecipient()))
        .filter(row -> row.getPayload() != null && !row.getPayload().isBlank())
        .sorted(
            Comparator.comparing(OutboundMessage::getCreatedAt, oldestFirstNullsLast())
                .thenComparing(OutboundMessage::getId))
        .map(row -> parse(objectMapper, row))
        .filter(payload -> payload != null && payload.type() == type)
        .map(EmailPayload::otp)
        .toList();
  }

  /** How many queued emails of this type were written for {@code recipient}. */
  public static long count(
      OutboundMessageRepository repository,
      ObjectMapper objectMapper,
      String recipient,
      EmailPayload.Type type) {
    return all(repository, objectMapper, recipient, type).size();
  }

  private static Comparator<Instant> oldestFirstNullsLast() {
    return Comparator.nullsLast(Comparator.naturalOrder());
  }

  private static EmailPayload parse(ObjectMapper objectMapper, OutboundMessage row) {
    try {
      return objectMapper.readValue(row.getPayload(), EmailPayload.class);
    } catch (Exception e) {
      throw new AssertionError("Queued payload is not readable as an EmailPayload", e);
    }
  }
}
