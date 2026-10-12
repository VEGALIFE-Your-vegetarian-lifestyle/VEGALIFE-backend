package com.vegalife.scheduled;

import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.repository.outbound.OutboundMessageQueueDao;
import com.vegalife.service.outbound.OutboundChannelAdapter;
import com.vegalife.shared.config.OutboundMessageProperties;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    } catch (RuntimeException e) {
      scheduleRetry(message, now, e);
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

  private void scheduleRetry(OutboundMessage message, Instant from, RuntimeException cause) {
    int attempts = Math.max(message.getAttempts(), 1);
    List<Duration> delays = properties.getRetry().getDelays();
    if (attempts <= delays.size()) {
      Duration delay = delays.get(attempts - 1);
      if (queueDao.scheduleRetry(
          message.getId(), OutboundStatus.PENDING, from.plus(delay), workerId)) {
        retried.incrementAndGet();
        log.warn(
            "Outbound message {} delivery failed on attempt {}/{}, retrying in {}: {}",
            message.getId(),
            attempts,
            attempts + 1,
            delay,
            cause.getMessage());
      } else {
        logLostOwnership(message, OutboundStatus.PENDING);
      }
    } else {
      Instant deferredAt = from.plus(properties.getRetry().getDeferredDelay());
      if (queueDao.scheduleRetry(message.getId(), OutboundStatus.DEFERRED, deferredAt, workerId)) {
        retried.incrementAndGet();
        deferred.incrementAndGet();
        log.error(
            "Outbound message {} failed after {} attempts, deferred until {}: {}",
            message.getId(),
            attempts,
            deferredAt,
            cause.getMessage());
      } else {
        logLostOwnership(message, OutboundStatus.DEFERRED);
      }
    }
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
