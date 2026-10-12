package com.vegalife.unit.repository.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class OutboundMessageRepositoryTest {

  private static final String PAYLOAD_JSON =
      "{\"type\":\"EMAIL_VERIFICATION\",\"username\":\"john\",\"otp\":\"123456\","
          + "\"expiryMinutes\":10}";

  @Autowired private OutboundMessageRepository outboundMessageRepository;

  @Autowired private TestEntityManager entityManager;

  @Test
  void save_outboundMessageWithJsonPayload_roundTripsExactly() {
    OutboundMessage message =
        OutboundMessage.builder()
            .channel(OutboundChannel.EMAIL)
            .recipient("john@example.com")
            .payload(PAYLOAD_JSON)
            .status(OutboundStatus.PENDING)
            .nextAttemptAt(Instant.now())
            .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
            .build();

    outboundMessageRepository.save(message);
    entityManager.flush();
    entityManager.clear();

    OutboundMessage found = outboundMessageRepository.findById(message.getId()).orElseThrow();

    assertThat(found.getPayload()).isEqualTo(PAYLOAD_JSON);
    assertThat(found.getRecipient()).isEqualTo("john@example.com");
    assertThat(found.getChannel()).isEqualTo(OutboundChannel.EMAIL);
    assertThat(found.getStatus()).isEqualTo(OutboundStatus.PENDING);
    assertThat(found.getAttempts()).isZero();
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.isTerminal()).isFalse();
  }

  @Test
  void deleteTerminalOlderThan_oldCompletedRowsDeleted() {
    OutboundMessage oldCompleted =
        OutboundMessage.builder()
            .channel(OutboundChannel.EMAIL)
            .recipient("old@example.com")
            .payload("{}")
            .status(OutboundStatus.COMPLETED)
            .nextAttemptAt(Instant.now().minus(10, ChronoUnit.DAYS))
            .createdAt(Instant.now().minus(10, ChronoUnit.DAYS))
            .build();
    OutboundMessage recentPending =
        OutboundMessage.builder()
            .channel(OutboundChannel.EMAIL)
            .recipient("recent@example.com")
            .payload("{}")
            .status(OutboundStatus.PENDING)
            .nextAttemptAt(Instant.now())
            .build();
    entityManager.persistAndFlush(oldCompleted);
    entityManager.persistAndFlush(recentPending);
    entityManager.clear();

    int deleted =
        outboundMessageRepository.deleteTerminalOlderThan(
            java.util.List.of(OutboundStatus.COMPLETED, OutboundStatus.FAILED),
            Instant.now().minus(7, ChronoUnit.DAYS));
    entityManager.flush();
    entityManager.clear();

    assertThat(deleted).isEqualTo(1);
    assertThat(outboundMessageRepository.findById(oldCompleted.getId())).isEmpty();
    assertThat(outboundMessageRepository.findById(recentPending.getId())).isPresent();
  }
}
