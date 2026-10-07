package com.vegalife.repository.subscription;

import com.vegalife.model.subscription.AiSubscription;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiSubscriptionRepository extends JpaRepository<AiSubscription, UUID> {

  Optional<AiSubscription> findByUserId(UUID userId);

  List<AiSubscription> findAllByUserId(UUID userId);

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

  @Modifying
  @Query(
      "update AiSubscription s set s.status = :cancelled, s.cancelledAt = :cancelledAt"
          + " where s.userId = :userId and s.status = :scheduled")
  int cancelScheduledForUser(
      @Param("userId") UUID userId,
      @Param("cancelledAt") Instant cancelledAt,
      @Param("cancelled") AiSubscription.Status cancelled,
      @Param("scheduled") AiSubscription.Status scheduled);
}
