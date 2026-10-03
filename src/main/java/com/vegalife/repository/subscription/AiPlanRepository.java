package com.vegalife.repository.subscription;

import com.vegalife.model.subscription.AiPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiPlanRepository extends JpaRepository<AiPlan, UUID> {

  Optional<AiPlan> findByCode(String code);
}
