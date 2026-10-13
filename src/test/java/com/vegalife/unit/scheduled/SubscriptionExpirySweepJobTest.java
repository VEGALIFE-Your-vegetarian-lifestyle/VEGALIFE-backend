package com.vegalife.unit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.scheduled.SubscriptionExpirySweepJob;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirySweepJobTest {

  @Mock private AiSubscriptionRepository subscriptionRepository;

  private final List<AiSubscription> store = new ArrayList<>();

  @BeforeEach
  void setUp() {
    store.clear();
  }

  private void stubQueries() {
    when(subscriptionRepository.findDueForExpiry(any()))
        .thenAnswer(
            invocation -> {
              Instant now = invocation.getArgument(0);
              return store.stream()
                  .filter(
                      row ->
                          (row.getStatus() == AiSubscription.Status.active
                                  || row.getStatus() == AiSubscription.Status.past_due)
                              && row.getRenewalDate() != null
                              && !row.getRenewalDate().isAfter(now))
                  .toList();
            });
    when(subscriptionRepository.findScheduledForUser(any()))
        .thenAnswer(
            invocation ->
                store.stream()
                    .filter(
                        row ->
                            row.getStatus() == AiSubscription.Status.scheduled
                                && row.getUserId().equals(invocation.<UUID>getArgument(0)))
                    .toList());
  }

  private AiSubscription row(
      AiSubscription.Status status, Instant renewalDate, UUID userId, Instant startedAt) {
    AiSubscription subscription = new AiSubscription();
    subscription.setId(UUID.randomUUID());
    subscription.setUserId(userId);
    subscription.setPlanId(UUID.randomUUID());
    subscription.setStatus(status);
    subscription.setRenewalDate(renewalDate);
    subscription.setStartedAt(startedAt);
    store.add(subscription);
    return subscription;
  }

  @Test
  void expireDueSubscriptions_whenScheduledSuccessorExists_expiresDueAndPromotesSuccessor() {
    stubQueries();
    UUID userId = UUID.randomUUID();
    Instant dueRenewal = Instant.now().minus(2, ChronoUnit.HOURS);
    Instant successorRenewal = Instant.now().plus(30, ChronoUnit.DAYS);
    Instant successorStarted = Instant.now().minus(5, ChronoUnit.MINUTES);
    AiSubscription due =
        row(
            AiSubscription.Status.active,
            dueRenewal,
            userId,
            Instant.now().minus(30, ChronoUnit.DAYS));
    AiSubscription successor =
        row(AiSubscription.Status.scheduled, successorRenewal, userId, successorStarted);
    SubscriptionExpirySweepJob job = new SubscriptionExpirySweepJob(subscriptionRepository, true);

    job.expireDueSubscriptions();

    assertThat(due.getStatus()).isEqualTo(AiSubscription.Status.expired);
    assertThat(successor.getStatus()).isEqualTo(AiSubscription.Status.active);
    assertThat(successor.getRenewalDate()).isEqualTo(successorRenewal);
    assertThat(successor.getStartedAt()).isEqualTo(successorStarted);
    verify(subscriptionRepository).save(due);
    verify(subscriptionRepository).save(successor);
  }

  @Test
  void expireDueSubscriptions_whenNoSuccessor_expiresDueRowOnly() {
    stubQueries();
    AiSubscription due =
        row(
            AiSubscription.Status.past_due,
            Instant.now().minus(1, ChronoUnit.HOURS),
            UUID.randomUUID(),
            Instant.now().minus(60, ChronoUnit.DAYS));
    SubscriptionExpirySweepJob job = new SubscriptionExpirySweepJob(subscriptionRepository, true);

    job.expireDueSubscriptions();

    assertThat(due.getStatus()).isEqualTo(AiSubscription.Status.expired);
    verify(subscriptionRepository).save(due);
    verify(subscriptionRepository, times(1)).save(any(AiSubscription.class));
  }

  @Test
  void expireDueSubscriptions_whenRunTwice_secondRunIsNoOp() {
    stubQueries();
    UUID userId = UUID.randomUUID();
    AiSubscription due =
        row(
            AiSubscription.Status.active,
            Instant.now().minus(3, ChronoUnit.HOURS),
            userId,
            Instant.now().minus(30, ChronoUnit.DAYS));
    AiSubscription successor =
        row(
            AiSubscription.Status.scheduled,
            Instant.now().plus(30, ChronoUnit.DAYS),
            userId,
            Instant.now().minus(5, ChronoUnit.MINUTES));
    SubscriptionExpirySweepJob job = new SubscriptionExpirySweepJob(subscriptionRepository, true);

    job.expireDueSubscriptions();
    job.expireDueSubscriptions();

    assertThat(due.getStatus()).isEqualTo(AiSubscription.Status.expired);
    assertThat(successor.getStatus()).isEqualTo(AiSubscription.Status.active);
    verify(subscriptionRepository, times(2)).findDueForExpiry(any());
    verify(subscriptionRepository, times(2)).save(any(AiSubscription.class));
  }

  @Test
  void expireDueSubscriptions_whenSchedulingDisabled_touchesNothing() {
    SubscriptionExpirySweepJob job = new SubscriptionExpirySweepJob(subscriptionRepository, false);

    job.expireDueSubscriptions();

    verifyNoInteractions(subscriptionRepository);
  }
}
