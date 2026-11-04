package com.vegalife.repository.subscription;

import com.vegalife.model.subscription.AiSubscription;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Acquisition rows (issue #181, ADR-021). A row is an acquisition, not a subscription: it records
 * which plan a member acquired, when it was granted, and which payment paid for it.
 */
@Repository
public interface AiSubscriptionRepository extends JpaRepository<AiSubscription, UUID> {

  List<AiSubscription> findAllByUserId(UUID userId);

  Page<AiSubscription> findByUserId(UUID userId, Pageable pageable);

  /** The acquisition a payment produced, if fulfilment already reached it. */
  Optional<AiSubscription> findByPaymentId(UUID paymentId);

  List<AiSubscription> findAllByPaymentIdIn(Iterable<UUID> paymentIds);

  /**
   * The open (ungranted) checkout for this plan, if one is still reusable. The ledger status must
   * still be consulted afterwards: a failed payment also leaves {@code granted_at} null. Replaces
   * {@code PaymentLedgerRepository.findFirstByUserIdAndPlanIdAndStatusAndCreatedAtAfter...} and the
   * index that backed it, now that the plan lives on this row.
   */
  @Query(
      "select s from AiSubscription s"
          + " where s.userId = :userId and s.planId = :planId"
          + " and s.paymentId is not null and s.grantedAt is null"
          + " and s.createdAt > :createdAfter"
          + " order by s.createdAt desc")
  List<AiSubscription> findReusablePending(
      @Param("userId") UUID userId,
      @Param("planId") UUID planId,
      @Param("createdAfter") Instant createdAfter);

  /**
   * The member's total allowance: the sum of every granted plan's quota.
   *
   * <p>Deliberately does <strong>not</strong> filter {@code ai_plan.active}. Deactivating a plan
   * stops new acquisition; it must never revoke an allowance already granted.
   */
  @Query(
      "select coalesce(sum(p.requestQuota), 0) from AiSubscription s, AiPlan p"
          + " where p.id = s.planId and s.userId = :userId and s.grantedAt is not null")
  long sumGrantedQuota(@Param("userId") UUID userId);

  /**
   * Claims a free plan exactly once. Returns 1 when the row was created, 0 when the member already
   * holds it.
   *
   * <p>The partial unique index {@code uq_ai_subscription_one_claim} is the only guarantee: two
   * concurrent calls both pass any read-before-write check, but exactly one insert survives.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO ai_subscription (user_id, plan_id, granted_at)"
              + " SELECT :userId, p.id, NOW() FROM ai_plan p"
              + " WHERE p.id = :planId AND p.active AND p.price_amount = 0"
              + " ON CONFLICT (user_id, plan_id) WHERE payment_id IS NULL DO NOTHING",
      nativeQuery = true)
  int insertClaim(@Param("userId") UUID userId, @Param("planId") UUID planId);
}
