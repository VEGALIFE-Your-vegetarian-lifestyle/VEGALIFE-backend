package com.vegalife.scheduled;

import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily subscription expiry sweep (FR-012): every {@code active}/{@code past_due} row whose {@code
 * renewal_date} has passed becomes {@code expired}, and when the same user has a scheduled
 * successor — written by a same-plan extension (FR-006) — that successor is promoted to {@code
 * active} with its window unchanged. Idempotent: expired rows no longer match the due query, so a
 * repeat run is a no-op. Due rows are scanned through the {@code (status, renewal_date)} index
 * added in V27 (NFR-SCALE-001); the at-most-24h lag between {@code renewal_date} and the sweep is
 * accepted by FR-012.
 */
@Component
@Slf4j
public class SubscriptionExpirySweepJob {

  private final AiSubscriptionRepository subscriptionRepository;
  private final boolean schedulingEnabled;

  public SubscriptionExpirySweepJob(
      AiSubscriptionRepository subscriptionRepository,
      @Value("${app.scheduling.enabled:true}") boolean schedulingEnabled) {
    this.subscriptionRepository = subscriptionRepository;
    this.schedulingEnabled = schedulingEnabled;
  }

  @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
  @Transactional
  public void expireDueSubscriptions() {
    if (!schedulingEnabled) {
      log.debug("Subscription expiry sweep skipped: app.scheduling.enabled=false");
      return;
    }
    List<AiSubscription> due = subscriptionRepository.findDueForExpiry(Instant.now());
    if (due.isEmpty()) {
      return;
    }
    int expired = 0;
    int promoted = 0;
    for (AiSubscription subscription : due) {
      subscription.setStatus(AiSubscription.Status.expired);
      subscriptionRepository.save(subscription);
      expired++;
      List<AiSubscription> successors =
          subscriptionRepository.findScheduledForUser(subscription.getUserId());
      if (!successors.isEmpty()) {
        AiSubscription successor = successors.getFirst();
        successor.setStatus(AiSubscription.Status.active);
        subscriptionRepository.save(successor);
        promoted++;
        log.info(
            "Subscription {} expired; scheduled successor {} promoted to active",
            subscription.getId(),
            successor.getId());
      }
    }
    log.info("Subscription expiry sweep completed: {} expired, {} promoted", expired, promoted);
  }
}
