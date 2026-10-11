package com.vegalife.integration.repository.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.repository.outbound.OutboundMessageQueueDao;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Queue semantics the drainer depends on (ADR-005): FOR UPDATE SKIP LOCKED claiming stays disjoint
 * under concurrency, state transitions only succeed for the worker that owns the row, and stale
 * claims are reclaimed according to the fast-retry window.
 */
@Testcontainers
@SpringBootTest(properties = "app.scheduling.enabled=false")
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboundMessageQueueDaoIntegrationTest {

  private static final String WORKER_A = "worker-a";
  private static final String WORKER_B = "worker-b";

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("vegalife_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false);

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }

  @Autowired private OutboundMessageQueueDao queueDao;
  @Autowired private OutboundMessageRepository outboundMessageRepository;

  @BeforeEach
  void setUp() {
    outboundMessageRepository.deleteAll();
  }

  @Test
  void claimDue_concurrentWorkersReceiveDisjointClaims() throws Exception {
    Instant dueAt = Instant.now();
    List<UUID> seeded = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      seeded.add(enqueue(OutboundStatus.PENDING, 0, dueAt, null, null).getId());
    }

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<List<OutboundMessage>> futureA =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return queueDao.claimDue(Instant.now(), WORKER_A, 3);
              });
      Future<List<OutboundMessage>> futureB =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return queueDao.claimDue(Instant.now(), WORKER_B, 3);
              });

      List<OutboundMessage> claimedByA = futureA.get(10, TimeUnit.SECONDS);
      List<OutboundMessage> claimedByB = futureB.get(10, TimeUnit.SECONDS);

      assertThat(claimedByA).hasSize(3);
      assertThat(claimedByB).hasSize(3);

      List<UUID> idsA = claimedByA.stream().map(OutboundMessage::getId).toList();
      List<UUID> idsB = claimedByB.stream().map(OutboundMessage::getId).toList();
      assertThat(idsA).doesNotContainAnyElementsOf(idsB);
      List<UUID> union = new ArrayList<>(idsA);
      union.addAll(idsB);
      assertThat(union).containsExactlyInAnyOrderElementsOf(seeded);

      claimedByA.forEach(row -> assertClaimed(row, WORKER_A));
      claimedByB.forEach(row -> assertClaimed(row, WORKER_B));
    } finally {
      executor.shutdownNow();
    }

    assertThat(outboundMessageRepository.findAll())
        .allSatisfy(
            row -> {
              assertThat(row.getStatus()).isEqualTo(OutboundStatus.PROCESSING);
              assertThat(row.getAttempts()).isEqualTo(1);
            });
  }

  @Test
  void claimDue_skipsNotYetDueAndAlreadyClaimedRows() {
    OutboundMessage duePending =
        enqueue(OutboundStatus.PENDING, 0, Instant.now().minusSeconds(1), null, null);
    OutboundMessage dueDeferred =
        enqueue(OutboundStatus.DEFERRED, 3, Instant.now().minusSeconds(2), null, null);
    OutboundMessage notYetDue =
        enqueue(OutboundStatus.PENDING, 0, Instant.now().plusSeconds(60), null, null);
    OutboundMessage alreadyClaimed =
        enqueue(
            OutboundStatus.PROCESSING,
            1,
            Instant.now().minusSeconds(1),
            Instant.now().minusSeconds(5),
            "worker-x");

    List<OutboundMessage> claimed = queueDao.claimDue(Instant.now(), WORKER_A, 10);

    assertThat(claimed)
        .extracting(OutboundMessage::getId)
        .containsExactlyInAnyOrder(duePending.getId(), dueDeferred.getId());
    OutboundMessage claimedDeferred =
        claimed.stream()
            .filter(row -> row.getId().equals(dueDeferred.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(claimedDeferred.getAttempts()).isEqualTo(4);
    assertThat(claimedDeferred.getStatus()).isEqualTo(OutboundStatus.PROCESSING);
    assertThat(claimedDeferred.getLockedBy()).isEqualTo(WORKER_A);

    OutboundMessage skippedPending = reload(notYetDue.getId());
    assertThat(skippedPending.getStatus()).isEqualTo(OutboundStatus.PENDING);
    assertThat(skippedPending.getAttempts()).isZero();
    assertThat(skippedPending.getLockedBy()).isNull();

    OutboundMessage skippedClaimed = reload(alreadyClaimed.getId());
    assertThat(skippedClaimed.getStatus()).isEqualTo(OutboundStatus.PROCESSING);
    assertThat(skippedClaimed.getLockedBy()).isEqualTo("worker-x");
  }

  @Test
  void claimDue_incrementsAttemptsPastTheFastRetryWindow() {
    OutboundMessage exhausted =
        enqueue(OutboundStatus.DEFERRED, 4, Instant.now().minusSeconds(1), null, null);

    List<OutboundMessage> claimed = queueDao.claimDue(Instant.now(), WORKER_A, 10);

    assertThat(claimed)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getId()).isEqualTo(exhausted.getId());
              assertThat(row.getAttempts()).isEqualTo(5);
              assertThat(row.getStatus()).isEqualTo(OutboundStatus.PROCESSING);
              assertThat(row.getLockedBy()).isEqualTo(WORKER_A);
              assertThat(row.getLockedAt()).isNotNull();
            });
  }

  @Test
  void markCompleted_onlySucceedsForTheOwningWorker() {
    OutboundMessage row =
        enqueue(OutboundStatus.PROCESSING, 1, Instant.now(), Instant.now(), WORKER_A);

    assertThat(queueDao.markCompleted(row.getId(), WORKER_B, Instant.now())).isFalse();
    OutboundMessage untouched = reload(row.getId());
    assertThat(untouched.getStatus()).isEqualTo(OutboundStatus.PROCESSING);
    assertThat(untouched.getPayload()).isNotBlank();
    assertThat(untouched.getLockedBy()).isEqualTo(WORKER_A);

    assertThat(queueDao.markCompleted(row.getId(), WORKER_A, Instant.now())).isTrue();
    OutboundMessage completed = reload(row.getId());
    assertThat(completed.getStatus()).isEqualTo(OutboundStatus.COMPLETED);
    assertThat(completed.getPayload()).isNull();
    assertThat(completed.getLockedAt()).isNull();
    assertThat(completed.getLockedBy()).isNull();
    assertThat(completed.getCompletedAt()).isNotNull();
  }

  @Test
  void markTerminal_onlySucceedsForTheOwningWorker() {
    OutboundMessage row =
        enqueue(OutboundStatus.PROCESSING, 1, Instant.now(), Instant.now(), WORKER_A);

    assertThat(queueDao.markTerminal(row.getId(), OutboundStatus.EXPIRED, WORKER_B)).isFalse();
    OutboundMessage untouched = reload(row.getId());
    assertThat(untouched.getStatus()).isEqualTo(OutboundStatus.PROCESSING);
    assertThat(untouched.getPayload()).isNotBlank();
    assertThat(untouched.getLockedBy()).isEqualTo(WORKER_A);

    assertThat(queueDao.markTerminal(row.getId(), OutboundStatus.EXPIRED, WORKER_A)).isTrue();
    OutboundMessage expired = reload(row.getId());
    assertThat(expired.getStatus()).isEqualTo(OutboundStatus.EXPIRED);
    assertThat(expired.getPayload()).isNull();
    assertThat(expired.getLockedAt()).isNull();
    assertThat(expired.getLockedBy()).isNull();
    assertThat(expired.getCompletedAt()).isNull();
  }

  @Test
  void scheduleRetry_onlySucceedsForTheOwningWorker() {
    OutboundMessage row =
        enqueue(OutboundStatus.PROCESSING, 1, Instant.now(), Instant.now(), WORKER_A);
    Instant retryAt = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MICROS);

    assertThat(queueDao.scheduleRetry(row.getId(), OutboundStatus.PENDING, retryAt, WORKER_B))
        .isFalse();
    OutboundMessage untouched = reload(row.getId());
    assertThat(untouched.getStatus()).isEqualTo(OutboundStatus.PROCESSING);
    assertThat(untouched.getPayload()).isNotBlank();
    assertThat(untouched.getLockedBy()).isEqualTo(WORKER_A);

    assertThat(queueDao.scheduleRetry(row.getId(), OutboundStatus.PENDING, retryAt, WORKER_A))
        .isTrue();
    OutboundMessage retried = reload(row.getId());
    assertThat(retried.getStatus()).isEqualTo(OutboundStatus.PENDING);
    assertThat(retried.getNextAttemptAt()).isEqualTo(retryAt);
    assertThat(retried.getPayload()).isNotBlank();
    assertThat(retried.getLockedAt()).isNull();
    assertThat(retried.getLockedBy()).isNull();
  }

  @Test
  void reclaimStale_returnsStaleClaimToPendingWithinTheFastRetryWindow() {
    Instant nextAttemptAt = Instant.now().plusSeconds(45).truncatedTo(ChronoUnit.MICROS);
    OutboundMessage stale =
        enqueue(
            OutboundStatus.PROCESSING, 2, nextAttemptAt, Instant.now().minusSeconds(120), WORKER_A);
    OutboundMessage fresh =
        enqueue(
            OutboundStatus.PROCESSING,
            2,
            Instant.now().plusSeconds(30),
            Instant.now().minusSeconds(5),
            WORKER_B);

    int reclaimed =
        queueDao.reclaimStale(Instant.now().minusSeconds(60), 4, Instant.now().plusSeconds(300));

    assertThat(reclaimed).isEqualTo(1);
    OutboundMessage recovered = reload(stale.getId());
    assertThat(recovered.getStatus()).isEqualTo(OutboundStatus.PENDING);
    assertThat(recovered.getLockedAt()).isNull();
    assertThat(recovered.getLockedBy()).isNull();
    assertThat(recovered.getNextAttemptAt()).isEqualTo(nextAttemptAt);

    OutboundMessage untouched = reload(fresh.getId());
    assertThat(untouched.getStatus()).isEqualTo(OutboundStatus.PROCESSING);
    assertThat(untouched.getLockedBy()).isEqualTo(WORKER_B);
  }

  @Test
  void reclaimStale_defersStaleClaimThatExhaustedTheFastRetryWindow() {
    OutboundMessage stale =
        enqueue(
            OutboundStatus.PROCESSING,
            4,
            Instant.now().plusSeconds(30),
            Instant.now().minusSeconds(120),
            WORKER_A);
    Instant deferredAt = Instant.now().plusSeconds(300).truncatedTo(ChronoUnit.MICROS);

    int reclaimed = queueDao.reclaimStale(Instant.now().minusSeconds(60), 4, deferredAt);

    assertThat(reclaimed).isEqualTo(1);
    OutboundMessage deferred = reload(stale.getId());
    assertThat(deferred.getStatus()).isEqualTo(OutboundStatus.DEFERRED);
    assertThat(deferred.getNextAttemptAt()).isEqualTo(deferredAt);
    assertThat(deferred.getLockedAt()).isNull();
    assertThat(deferred.getLockedBy()).isNull();
  }

  private OutboundMessage enqueue(
      OutboundStatus status,
      int attempts,
      Instant nextAttemptAt,
      Instant lockedAt,
      String lockedBy) {
    return outboundMessageRepository.save(
        OutboundMessage.builder()
            .channel(OutboundChannel.EMAIL)
            .recipient("queue-test@example.com")
            .payload(
                "{\"type\":\"EMAIL_VERIFICATION\",\"username\":\"queue\","
                    + "\"otp\":\"123456\",\"expiryMinutes\":10}")
            .status(status)
            .attempts(attempts)
            .nextAttemptAt(nextAttemptAt.truncatedTo(ChronoUnit.MICROS))
            .lockedAt(lockedAt == null ? null : lockedAt.truncatedTo(ChronoUnit.MICROS))
            .lockedBy(lockedBy)
            .createdAt(Instant.now().truncatedTo(ChronoUnit.MICROS))
            .build());
  }

  private OutboundMessage reload(UUID id) {
    return outboundMessageRepository.findById(id).orElseThrow();
  }

  private static void assertClaimed(OutboundMessage row, String workerId) {
    assertThat(row.getStatus()).isEqualTo(OutboundStatus.PROCESSING);
    assertThat(row.getAttempts()).isEqualTo(1);
    assertThat(row.getLockedBy()).isEqualTo(workerId);
    assertThat(row.getLockedAt()).isNotNull();
  }
}
