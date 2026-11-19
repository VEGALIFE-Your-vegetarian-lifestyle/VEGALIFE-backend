package com.vegalife.repository.subscription;

import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.model.subscription.PaymentLedger;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentLedgerRepository
    extends JpaRepository<PaymentLedger, UUID>, JpaSpecificationExecutor<PaymentLedger> {

  Optional<PaymentLedger> findByTxnRef(String txnRef);

  Optional<PaymentLedger> findByIdAndUserId(UUID id, UUID userId);

  Page<PaymentLedger> findByUserId(UUID userId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PaymentLedger p where p.id = :id")
  Optional<PaymentLedger> findByIdForUpdate(@Param("id") UUID id);

  /**
   * Admin dashboard: lifetime money actually received. Only {@code succeeded} rows carry a {@code
   * paid_at}, and there is no refund write path, so this total is gross by construction.
   */
  @Query("select coalesce(sum(l.amount), 0) from PaymentLedger l where l.status = :status")
  long sumAmountByStatus(@Param("status") PaymentLedgerStatus status);

  /** Admin dashboard: lifetime count of payments in one state. */
  long countByStatus(PaymentLedgerStatus status);

  /**
   * Admin dashboard: how many distinct members have ever paid. Distinct because a member may hold
   * several acquisitions and each produces its own ledger row.
   */
  @Query("select count(distinct l.userId) from PaymentLedger l where l.status = :status")
  long countDistinctUsersByStatus(@Param("status") PaymentLedgerStatus status);

  /**
   * Admin dashboard: payments still pending after {@code cutoff} — money that tried and never
   * arrived. Nothing expires or reconciles them, so this only grows.
   */
  long countByStatusAndCreatedAtBefore(PaymentLedgerStatus status, Instant cutoff);
}
