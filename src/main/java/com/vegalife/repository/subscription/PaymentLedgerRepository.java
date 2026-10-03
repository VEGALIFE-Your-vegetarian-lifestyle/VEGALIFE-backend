package com.vegalife.repository.subscription;

import com.vegalife.model.subscription.PaymentLedger;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentLedgerRepository extends JpaRepository<PaymentLedger, UUID> {

  Optional<PaymentLedger> findFirstByUserIdAndPlanIdAndStatusOrderByPaidAtDesc(
      UUID userId, UUID planId, PaymentLedger.Status status);
}
