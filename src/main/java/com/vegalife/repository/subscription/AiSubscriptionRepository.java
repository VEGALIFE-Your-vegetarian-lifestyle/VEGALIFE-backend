package com.vegalife.repository.subscription;

import com.vegalife.model.subscription.AiSubscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiSubscriptionRepository extends JpaRepository<AiSubscription, UUID> {

  Optional<AiSubscription> findByUserId(UUID userId);
}
