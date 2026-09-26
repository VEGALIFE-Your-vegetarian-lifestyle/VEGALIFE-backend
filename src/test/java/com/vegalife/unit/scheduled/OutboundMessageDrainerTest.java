package com.vegalife.unit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.repository.outbound.OutboundMessageQueueDao;
import com.vegalife.scheduled.OutboundMessageDrainer;
import com.vegalife.service.outbound.OutboundChannelAdapter;
import com.vegalife.shared.config.OutboundMessageProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OutboundMessageDrainerTest {

  private static final int BATCH_SIZE = 10;

  @Mock private OutboundMessageQueueDao queueDao;
  @Mock private OutboundChannelAdapter adapter;

  private OutboundMessageProperties properties;
  private OutboundMessageDrainer drainer;

  @BeforeEach
  void setUp() {
    properties = new OutboundMessageProperties();
    when(adapter.channel()).thenReturn(OutboundChannel.EMAIL);
    drainer = new OutboundMessageDrainer(queueDao, List.of(adapter), properties, true);
  }

  @Test
  void shouldCompleteDeliveredMessageWithoutRetry() {
    OutboundMessage message = message(1, Instant.now(), null);
    when(queueDao.claimDue(any(Instant.class), anyString(), eq(BATCH_SIZE)))
        .thenReturn(List.of(message));
    when(queueDao.markCompleted(eq(message.getId()), anyString(), any(Instant.class)))
        .thenReturn(true);

    drainer.drainOnce();

    verify(adapter).deliver(message);
    verify(queueDao).markCompleted(eq(message.getId()), anyString(), any(Instant.class));
    verify(queueDao, never())
        .scheduleRetry(any(UUID.class), any(OutboundStatus.class), any(Instant.class), anyString());
  }

  @Test
  void shouldRetryWithFirstIncreasingDelayAfterFirstFailedAttempt() {
    OutboundMessage message = message(1, Instant.now(), null);
    when(queueDao.claimDue(any(Instant.class), anyString(), eq(BATCH_SIZE)))
        .thenReturn(List.of(message));
    doThrow(new RuntimeException("smtp down")).when(adapter).deliver(message);
    when(queueDao.scheduleRetry(
            eq(message.getId()), eq(OutboundStatus.PENDING), any(Instant.class), anyString()))
        .thenReturn(true);

    Instant before = Instant.now();
    drainer.drainOnce();

    ArgumentCaptor<Instant> nextAttemptAt = ArgumentCaptor.forClass(Instant.class);
    verify(queueDao)
        .scheduleRetry(
            eq(message.getId()), eq(OutboundStatus.PENDING), nextAttemptAt.capture(), anyString());
    assertThat(nextAttemptAt.getValue())
        .isBetween(before, before.plus(Duration.ofSeconds(10)).plusSeconds(5));
  }

  @Test
  void shouldRetryWithLargestDelayOnThirdFailedAttempt() {
    OutboundMessage message = message(3, Instant.now(), null);
    when(queueDao.claimDue(any(Instant.class), anyString(), eq(BATCH_SIZE)))
        .thenReturn(List.of(message));
    doThrow(new RuntimeException("smtp down")).when(adapter).deliver(message);
    when(queueDao.scheduleRetry(
            eq(message.getId()), eq(OutboundStatus.PENDING), any(Instant.class), anyString()))
        .thenReturn(true);

    Instant before = Instant.now();
    drainer.drainOnce();

    ArgumentCaptor<Instant> nextAttemptAt = ArgumentCaptor.forClass(Instant.class);
    verify(queueDao)
        .scheduleRetry(
            eq(message.getId()), eq(OutboundStatus.PENDING), nextAttemptAt.capture(), anyString());
    assertThat(nextAttemptAt.getValue())
        .isBetween(before, before.plus(Duration.ofMinutes(2)).plusSeconds(5));
  }

  @Test
  void shouldDeferAfterFastRetriesAreExhausted() {
    OutboundMessage message = message(4, Instant.now(), null);
    when(queueDao.claimDue(any(Instant.class), anyString(), eq(BATCH_SIZE)))
        .thenReturn(List.of(message));
    doThrow(new RuntimeException("smtp down")).when(adapter).deliver(message);
    when(queueDao.scheduleRetry(
            eq(message.getId()), eq(OutboundStatus.DEFERRED), any(Instant.class), anyString()))
        .thenReturn(true);

    Instant before = Instant.now();
    drainer.drainOnce();

    ArgumentCaptor<Instant> nextAttemptAt = ArgumentCaptor.forClass(Instant.class);
    verify(queueDao)
        .scheduleRetry(
            eq(message.getId()), eq(OutboundStatus.DEFERRED), nextAttemptAt.capture(), anyString());
    assertThat(nextAttemptAt.getValue())
        .isBetween(before, before.plus(Duration.ofMinutes(5)).plusSeconds(5));
  }

  @Test
  void shouldExpireMessagePastItsBusinessDeadlineWithoutDelivery() {
    OutboundMessage message = message(1, Instant.now(), Instant.now().minusSeconds(5));
    when(queueDao.claimDue(any(Instant.class), anyString(), eq(BATCH_SIZE)))
        .thenReturn(List.of(message));
    when(queueDao.markTerminal(eq(message.getId()), eq(OutboundStatus.EXPIRED), anyString()))
        .thenReturn(true);

    drainer.drainOnce();

    verify(adapter, never()).deliver(any(OutboundMessage.class));
    verify(queueDao).markTerminal(eq(message.getId()), eq(OutboundStatus.EXPIRED), anyString());
  }

  @Test
  void shouldFailMessagePastMaxAgeWithoutDelivery() {
    OutboundMessage message = message(1, Instant.now().minus(Duration.ofHours(25)), null);
    when(queueDao.claimDue(any(Instant.class), anyString(), eq(BATCH_SIZE)))
        .thenReturn(List.of(message));
    when(queueDao.markTerminal(eq(message.getId()), eq(OutboundStatus.FAILED), anyString()))
        .thenReturn(true);

    drainer.drainOnce();

    verify(adapter, never()).deliver(any(OutboundMessage.class));
    verify(queueDao).markTerminal(eq(message.getId()), eq(OutboundStatus.FAILED), anyString());
  }

  @Test
  void shouldReclaimStaleClaimsWithFastAttemptLimit() {
    when(queueDao.reclaimStale(any(Instant.class), eq(4), any(Instant.class))).thenReturn(2);
    when(queueDao.claimDue(any(Instant.class), anyString(), anyInt())).thenReturn(List.of());

    drainer.drainOnce();

    verify(queueDao).reclaimStale(any(Instant.class), eq(4), any(Instant.class));
  }

  @Test
  void shouldDoNothingWhenSchedulingDisabled() {
    OutboundMessageDrainer disabled =
        new OutboundMessageDrainer(queueDao, List.of(adapter), properties, false);

    disabled.poll();

    verifyNoInteractions(queueDao);
    verify(adapter, never()).deliver(any(OutboundMessage.class));
  }

  @Test
  void shouldRedeliverClaimedRowWhenCompletionGuardRejectsIt() {
    OutboundMessage message = message(1, Instant.now(), null);
    when(queueDao.claimDue(any(Instant.class), anyString(), eq(BATCH_SIZE)))
        .thenReturn(List.of(message));
    when(queueDao.markCompleted(eq(message.getId()), anyString(), any(Instant.class)))
        .thenReturn(false);

    drainer.drainOnce();
    drainer.drainOnce();

    verify(adapter, times(2)).deliver(message);
    verify(queueDao, times(2)).markCompleted(eq(message.getId()), anyString(), any(Instant.class));
  }

  @Test
  void shouldExpireMessageThatIsPastBothDeadlineAndMaxAge() {
    OutboundMessage message =
        message(2, Instant.now().minus(Duration.ofHours(25)), Instant.now().minusSeconds(5));
    when(queueDao.claimDue(any(Instant.class), anyString(), eq(BATCH_SIZE)))
        .thenReturn(List.of(message));
    when(queueDao.markTerminal(eq(message.getId()), eq(OutboundStatus.EXPIRED), anyString()))
        .thenReturn(true);

    drainer.drainOnce();

    verify(adapter, never()).deliver(any(OutboundMessage.class));
    verify(queueDao).markTerminal(eq(message.getId()), eq(OutboundStatus.EXPIRED), anyString());
    verify(queueDao, never())
        .markTerminal(eq(message.getId()), eq(OutboundStatus.FAILED), anyString());
  }

  @Test
  void shouldLogDeliverySummaryCountersSinceLastReport() {
    OutboundMessage delivered = message(1, Instant.now(), null);
    OutboundMessage failing = message(1, Instant.now(), null);
    when(queueDao.claimDue(any(Instant.class), anyString(), eq(BATCH_SIZE)))
        .thenReturn(List.of(delivered, failing));
    when(queueDao.markCompleted(eq(delivered.getId()), anyString(), any(Instant.class)))
        .thenReturn(true);
    when(queueDao.scheduleRetry(
            eq(failing.getId()), eq(OutboundStatus.PENDING), any(Instant.class), anyString()))
        .thenReturn(true);
    doNothing().when(adapter).deliver(delivered);
    doThrow(new RuntimeException("smtp down")).when(adapter).deliver(failing);

    Logger logger = (Logger) LoggerFactory.getLogger(OutboundMessageDrainer.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      ReflectionTestUtils.setField(drainer, "lastSummaryAtMs", 0L);

      drainer.drainOnce();

      List<String> summaries =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .filter(line -> line.startsWith("Outbound queue summary"))
              .toList();
      assertThat(summaries)
          .singleElement()
          .isEqualTo(
              "Outbound queue summary since last report: sent=1, retried=1, deferred=0,"
                  + " expired=0, failed=0, reclaimed=0");
    } finally {
      logger.detachAppender(appender);
    }
  }

  private static OutboundMessage message(int attempts, Instant createdAt, Instant expiresAt) {
    return OutboundMessage.builder()
        .id(UUID.randomUUID())
        .channel(OutboundChannel.EMAIL)
        .recipient("user@example.com")
        .payload(
            "{\"type\":\"EMAIL_VERIFICATION\",\"username\":\"john\","
                + "\"otp\":\"123456\",\"expiryMinutes\":10}")
        .status(OutboundStatus.PROCESSING)
        .attempts(attempts)
        .nextAttemptAt(Instant.now())
        .createdAt(createdAt)
        .expiresAt(expiresAt)
        .build();
  }
}
