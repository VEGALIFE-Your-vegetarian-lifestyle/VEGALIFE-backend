package com.vegalife.unit.repository.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.subscription.PaymentLedgerSpecifications;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class PaymentLedgerSpecificationsTest {

  @Autowired private PaymentLedgerRepository paymentLedgerRepository;

  @Autowired private EntityManager entityManager;

  private final UUID userA = UUID.randomUUID();
  private final UUID userB = UUID.randomUUID();
  private final UUID planId = UUID.randomUUID();

  @Test
  void withFilters_noFilters_returnsAllRows() {
    createLedger(userA, PaymentLedger.Status.succeeded, null);
    createLedger(userA, PaymentLedger.Status.failed, null);
    createLedger(userB, PaymentLedger.Status.pending, null);
    flushAndClear();

    List<PaymentLedger> result = findAll(null, null, null, null);

    assertThat(result).hasSize(3);
  }

  @Test
  void withFilters_byUser_returnsOnlyThatUsersRows() {
    createLedger(userA, PaymentLedger.Status.succeeded, null);
    createLedger(userA, PaymentLedger.Status.failed, null);
    createLedger(userB, PaymentLedger.Status.succeeded, null);
    flushAndClear();

    List<PaymentLedger> result = findAll(userA, null, null, null);

    assertThat(result).hasSize(2).allMatch(l -> userA.equals(l.getUserId()));
  }

  @Test
  void withFilters_byStatus_returnsOnlyMatchingStatus() {
    createLedger(userA, PaymentLedger.Status.succeeded, null);
    createLedger(userA, PaymentLedger.Status.succeeded, null);
    createLedger(userB, PaymentLedger.Status.refunded, null);
    flushAndClear();

    List<PaymentLedger> result = findAll(null, PaymentLedger.Status.succeeded, null, null);

    assertThat(result).hasSize(2).allMatch(l -> l.getStatus() == PaymentLedger.Status.succeeded);
  }

  @Test
  void withFilters_createdFromInclusive_includesRowExactlyOnBound() {
    PaymentLedger onBound = createLedger(userA, PaymentLedger.Status.succeeded, null);
    PaymentLedger before = createLedger(userA, PaymentLedger.Status.succeeded, null);
    PaymentLedger after = createLedger(userA, PaymentLedger.Status.succeeded, null);
    setCreatedAt(onBound, Instant.parse("2026-03-01T00:00:00Z"));
    setCreatedAt(before, Instant.parse("2026-02-28T23:59:59Z"));
    setCreatedAt(after, Instant.parse("2026-03-02T00:00:00Z"));
    flushAndClear();
    List<PaymentLedger> result = findAll(null, null, Instant.parse("2026-03-01T00:00:00Z"), null);

    assertThat(result)
        .extracting(PaymentLedger::getId)
        .containsExactlyInAnyOrder(onBound.getId(), after.getId())
        .doesNotContain(before.getId());
  }

  @Test
  void withFilters_createdToInclusive_includesRowExactlyOnBound() {
    PaymentLedger onBound = createLedger(userA, PaymentLedger.Status.succeeded, null);
    PaymentLedger before = createLedger(userA, PaymentLedger.Status.succeeded, null);
    PaymentLedger after = createLedger(userA, PaymentLedger.Status.succeeded, null);
    setCreatedAt(onBound, Instant.parse("2026-03-01T00:00:00Z"));
    setCreatedAt(before, Instant.parse("2026-02-28T23:59:59Z"));
    setCreatedAt(after, Instant.parse("2026-03-01T00:00:01Z"));
    flushAndClear();

    List<PaymentLedger> result = findAll(null, null, null, Instant.parse("2026-03-01T00:00:00Z"));

    assertThat(result)
        .extracting(PaymentLedger::getId)
        .containsExactlyInAnyOrder(onBound.getId(), before.getId());
  }

  @Test
  void withFilters_createdRange_excludesRowsOutsideBounds() {
    PaymentLedger old = createLedger(userA, PaymentLedger.Status.succeeded, null);
    PaymentLedger inRange = createLedger(userA, PaymentLedger.Status.succeeded, null);
    PaymentLedger future = createLedger(userA, PaymentLedger.Status.succeeded, null);
    setCreatedAt(old, Instant.now().minus(30, ChronoUnit.DAYS));
    setCreatedAt(inRange, Instant.now().minus(1, ChronoUnit.DAYS));
    setCreatedAt(future, Instant.now().plus(30, ChronoUnit.DAYS));
    flushAndClear();

    List<PaymentLedger> result =
        findAll(
            null,
            null,
            Instant.now().minus(7, ChronoUnit.DAYS),
            Instant.now().plus(1, ChronoUnit.HOURS));

    assertThat(result).extracting(PaymentLedger::getId).containsExactly(inRange.getId());
  }

  @Test
  void withFilters_combinedFilters_narrowsToIntersection() {
    createLedger(userA, PaymentLedger.Status.succeeded, null);
    createLedger(userA, PaymentLedger.Status.failed, null);
    createLedger(userB, PaymentLedger.Status.succeeded, null);
    createLedger(userB, PaymentLedger.Status.failed, null);
    flushAndClear();

    List<PaymentLedger> result = findAll(userA, PaymentLedger.Status.succeeded, null, null);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getUserId()).isEqualTo(userA);
    assertThat(result.getFirst().getStatus()).isEqualTo(PaymentLedger.Status.succeeded);
  }

  private List<PaymentLedger> findAll(
      UUID userId, PaymentLedger.Status status, Instant createdFrom, Instant createdTo) {
    return paymentLedgerRepository.findAll(
        PaymentLedgerSpecifications.withFilters(userId, status, createdFrom, createdTo));
  }

  private PaymentLedger createLedger(UUID userId, PaymentLedger.Status status, Instant createdAt) {
    PaymentLedger ledger =
        PaymentLedger.builder()
            .userId(userId)
            .planId(planId)
            .amount(49000L)
            .currency("VND")
            .status(status)
            .provider("vnpay")
            .txnRef("TXN-" + UUID.randomUUID())
            .createdAt(createdAt)
            .build();
    entityManager.persist(ledger);
    return ledger;
  }

  private void setCreatedAt(PaymentLedger ledger, Instant createdAt) {
    entityManager
        .createNativeQuery("UPDATE payment_ledger SET created_at = :ts WHERE id = :id")
        .setParameter("ts", createdAt)
        .setParameter("id", ledger.getId())
        .executeUpdate();
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
