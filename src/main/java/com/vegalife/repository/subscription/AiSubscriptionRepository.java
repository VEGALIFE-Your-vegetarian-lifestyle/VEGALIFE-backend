package com.vegalife.repository.subscription;

import com.vegalife.model.subscription.AiSubscription;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiSubscriptionRepository extends JpaRepository<AiSubscription, UUID> {

  Optional<AiSubscription> findByUserId(UUID userId);

  @Query(
      "select s from AiSubscription s"
          + " where s.userId = :userId and s.status in ('active', 'past_due')")
  Optional<AiSubscription> findInEffect(@Param("userId") UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from AiSubscription s"
          + " where s.userId = :userId and s.status in ('active', 'past_due')")
  Optional<AiSubscription> findInEffectForUpdate(@Param("userId") UUID userId);

  boolean existsByUserIdAndPlanIdAndStatus(UUID userId, UUID planId, AiSubscription.Status status);
}
