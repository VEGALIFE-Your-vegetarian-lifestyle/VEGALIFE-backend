package com.vegalife.scheduled;

import com.vegalife.enums.outbound.OutboundChannel;
import com.vegalife.enums.outbound.OutboundStatus;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.repository.outbound.OutboundMessageQueueDao;
import com.vegalife.service.outbound.OutboundChannelAdapter;
import com.vegalife.service.outbound.OutboundMessageProperties;
import com.vegalife.shared.exception.RetryableDeliveryException;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the persistent outbound queue (ADR-005): reclaims stale claims, claims due rows, delivers
 * them through the matching channel adapter, and settles each row as completed, retried, deferred,
 * expired, or failed. Delivery failures never propagate to the caller that enqueued the message.
 */
@Component
@Slf4j
public class OutboundMessageDrainer {

  private static final long SUMMARY_INTERVAL_MS = 60_000;

  private final OutboundMessageQueueDao queueDao;
  private final Map<OutboundChannel, OutboundChannelAdapter> adapters;
  private final OutboundMessageProperties properties;
  private final boolean schedulingEnabled;
  private final String workerId;

  private long lastSummaryAtMs = System.currentTimeMillis();
  private final AtomicLong sent = new AtomicLong();
  private final AtomicLong retried = new AtomicLong();
  private final AtomicLong deferred = new AtomicLong();
  private final AtomicLong expired = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();
  private final AtomicLong reclaimed = new AtomicLong();

  public OutboundMessageDrainer(
      OutboundMessageQueueDao queueDao,
      List<OutboundChannelAdapter> adapters,
      OutboundMessageProperties properties,
      @Value("${app.scheduling.enabled:true}") boolean schedulingEnabled) {
    this.queueDao = queueDao;
    this.adapters =
        adapters.stream()
            .collect(Collectors.toMap(OutboundChannelAdapter::channel, Function.identity()));
    this.properties = properties;
    this.schedulingEnabled = schedulingEnabled;
    this.workerId = newWorkerId();
  }

  @Scheduled(fixedDelayString = "${app.outbound.polling.interval:5s}")
  public void poll() {
    if (!schedulingEnabled) {
      return;
    }
    drainOnce();
  }

  /** One reclaim-claim-deliver-settle pass; never throws. Exposed for tests and manual runs. */
  public void drainOnce() {
    try {
      reclaimStale();
      processDue();
      logSummaryIfDue();
    } catch (RuntimeException e) {
      log.error("Outbound queue drain failed", e);
    }
  }

  private void reclaimStale() {
    Instant now = Instant.now();
    int fastAttemptLimit = properties.getRetry().getDelays().size() + 1;
    int count =
        queueDao.reclaimStale(
            now.minus(properties.getVisibilityTimeout()),
            fastAttemptLimit,
            now.plus(properties.getRetry().getDeferredDelay()));
    if (count > 0) {
      reclaimed.addAndGet(count);
      log.warn(
          "Reclaimed {} stale PROCESSING outbound messages past visibility timeout {}",
          count,
          properties.getVisibilityTimeout());
    }
  }

  private void processDue() {
    List<OutboundMessage> batch =
        queueDao.claimDue(Instant.now(), workerId, properties.getBatchSize());
    for (OutboundMessage message : batch) {
      try {
        processOne(message);
      } catch (RuntimeException e) {
        log.error("Unexpected error processing outbound message {}", message.getId(), e);
        // Row stays PROCESSING; the reaper recovers it after the visibility timeout.
      }
    }
  }

  private void processOne(OutboundMessage message) {
    Instant now = Instant.now();
    if (isPastExpiry(message, now)) {
      if (markTerminal(message, OutboundStatus.EXPIRED)) {
        expired.incrementAndGet();
        log.warn(
            "Outbound message {} expired before delivery: attempts={}",
            message.getId(),
            message.getAttempts());
      }
      return;
    }
    if (isPastMaxAge(message, now)) {
      if (markTerminal(message, OutboundStatus.FAILED)) {
        failed.incrementAndGet();
        log.error(
            "Outbound message {} exceeded max age {} without delivery: attempts={}",
            message.getId(),
            properties.getMaxAge(),
            message.getAttempts());
      }
      return;
    }
    OutboundChannelAdapter adapter = adapters.get(message.getChannel());
    if (adapter == null) {
      if (markTerminal(message, OutboundStatus.FAILED)) {
        failed.incrementAndGet();
        log.error(
            "No adapter registered for outbound channel {}; message {} marked FAILED",
            message.getChannel(),
            message.getId());
      }
      return;
    }
    try {
      adapter.deliver(message);
    } catch (RetryableDeliveryException e) {
      scheduleRetry(message, now, e, e.getRetryAfter());
      return;
    } catch (RuntimeException e) {
      abandon(message, now, e);
      return;
    }
    if (queueDao.markCompleted(message.getId(), workerId, Instant.now())) {
      sent.incrementAndGet();
      log.debug(
          "Outbound message {} delivered on channel {}", message.getId(), message.getChannel());
    } else {
      log.warn(
          "Outbound message {} no longer owned by {} after delivery; not counted as sent",
          message.getId(),
          workerId);
    }
  }

  /**
   * Ends a message whose delivery can never succeed (ADR-020).
   *
   * <p>Retrying a permanent rejection costs a full day of the fast ladder plus the deferred
   * cadence, and ends in {@code FAILED} anyway — so it is marked terminal now, and the adapter is
   * told so it can record the domain consequence (the content filter queues the post for review).
   */
  private void abandon(OutboundMessage message, Instant now, RuntimeException cause) {
    if (!markTerminal(message, OutboundStatus.FAILED)) {
      return;
    }
    failed.incrementAndGet();
    log.error(
        "Outbound message {} abandoned after {} attempt(s) on channel {}: {}",
        message.getId(),
        message.getAttempts(),
        message.getChannel(),
        cause.getMessage());
    adapterFor(message).ifPresent(adapter -> notifyAbandoned(adapter, message, cause));
  }

  private Optional<OutboundChannelAdapter> adapterFor(OutboundMessage message) {
    return Optional.ofNullable(adapters.get(message.getChannel()));
  }

  /**
   * The adapter's hook still runs when it throws: losing the notification would leave the domain (a
   * post stuck at {@code PENDING}) in a worse state than the failed delivery itself.
   */
  private void notifyAbandoned(
      OutboundChannelAdapter adapter, OutboundMessage message, RuntimeException cause) {
    try {
      adapter.onDeliveryAbandoned(message, cause);
    } catch (RuntimeException e) {
      log.error(
          "Channel adapter {} failed to record abandonment of outbound message {}",
          message.getChannel(),
          message.getId(),
          e);
    }
  }

  /**
   * Reschedules a failed delivery, never earlier than the receiver's own retry hint.
   *
   * @param retryAfter the provider's requested delay, or {@code null} when it gave none
   */
  private void scheduleRetry(
      OutboundMessage message, Instant from, RuntimeException cause, Duration retryAfter) {
    int attempts = Math.max(message.getAttempts(), 1);
    List<Duration> delays = properties.getRetry().getDelays();
    boolean withinFastRetries = attempts <= delays.size();
    Duration configured =
        withinFastRetries ? delays.get(attempts - 1) : properties.getRetry().getDeferredDelay();
    // A provider's hint is a floor, never a ceiling: reacting sooner than asked re-arms the very
    // limit being waited on, which is how a queue throttles itself (ADR-020).
    Duration delay =
        retryAfter != null && retryAfter.compareTo(configured) > 0 ? retryAfter : configured;

    if (deferralWouldExceedMaxAge(message, from, delay)) {
      log.warn(
          "Outbound message {} cannot be retried inside max age {} (next attempt {}); abandoning: {}",
          message.getId(),
          properties.getMaxAge(),
          from.plus(delay),
          cause.getMessage());
      abandon(message, from, cause);
      return;
    }

    OutboundStatus status = withinFastRetries ? OutboundStatus.PENDING : OutboundStatus.DEFERRED;
    if (queueDao.scheduleRetry(message.getId(), status, from.plus(delay), workerId)) {
      retried.incrementAndGet();
      if (!withinFastRetries) {
        deferred.incrementAndGet();
      }
      String line = "Outbound message {} delivery failed on attempt {}/{}, retrying in {}: {}";
      if (withinFastRetries) {
        log.warn(line, message.getId(), attempts, attempts + 1, delay, cause.getMessage());
      } else {
        log.error(
            "Outbound message {} failed after {} attempts, deferred until {}: {}",
            message.getId(),
            attempts,
            from.plus(delay),
            cause.getMessage());
      }
    } else {
      logLostOwnership(message, status);
    }
  }

  /** True when the next attempt would land past the queue's max age, making it pointless. */
  private boolean deferralWouldExceedMaxAge(OutboundMessage message, Instant from, Duration delay) {
    if (message.getCreatedAt() == null) {
      return false;
    }
    return from.plus(delay).isAfter(message.getCreatedAt().plus(properties.getMaxAge()));
  }

  private boolean markTerminal(OutboundMessage message, OutboundStatus status) {
    if (queueDao.markTerminal(message.getId(), status, workerId)) {
      return true;
    }
    logLostOwnership(message, status);
    return false;
  }

  private void logLostOwnership(OutboundMessage message, OutboundStatus intended) {
    log.warn(
        "Outbound message {} no longer owned by {}; {} transition skipped",
        message.getId(),
        workerId,
        intended);
  }

  private boolean isPastExpiry(OutboundMessage message, Instant now) {
    return message.getExpiresAt() != null && !now.isBefore(message.getExpiresAt());
  }

  private boolean isPastMaxAge(OutboundMessage message, Instant now) {
    return message.getCreatedAt() != null
        && now.isAfter(message.getCreatedAt().plus(properties.getMaxAge()));
  }

  private void logSummaryIfDue() {
    long nowMs = System.currentTimeMillis();
    if (nowMs - lastSummaryAtMs < SUMMARY_INTERVAL_MS) {
      return;
    }
    lastSummaryAtMs = nowMs;
    log.info(
        "Outbound queue summary since last report: sent={}, retried={}, deferred={},"
            + " expired={}, failed={}, reclaimed={}",
        sent.getAndSet(0),
        retried.getAndSet(0),
        deferred.getAndSet(0),
        expired.getAndSet(0),
        failed.getAndSet(0),
        reclaimed.getAndSet(0));
  }

  private static String newWorkerId() {
    String runtime =
        ManagementFactory.getRuntimeMXBean().getName().replaceAll("[^A-Za-z0-9.-]", "-");
    String id = runtime + "-" + UUID.randomUUID().toString().substring(0, 8);
    return id.length() <= 64 ? id : id.substring(0, 64);
  }
}
