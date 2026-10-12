package com.vegalife.repository.subscription;

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

  Optional<PaymentLedger> findFirstByUserIdAndPlanIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
      UUID userId, UUID planId, PaymentLedger.Status status, Instant createdAtAfter);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PaymentLedger p where p.id = :id")
  Optional<PaymentLedger> findByIdForUpdate(@Param("id") UUID id);

  Optional<PaymentLedger> findFirstByUserIdAndPlanIdAndStatusOrderByPaidAtDesc(
      UUID userId, UUID planId, PaymentLedger.Status status);
}
