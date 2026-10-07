package com.vegalife.repository.ai;

import com.vegalife.model.ai.AiConversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

  /** Ownership check (BR-AI-003): a conversation is visible only to its owner. */
  Optional<AiConversation> findByIdAndUserId(UUID id, UUID userId);
}
