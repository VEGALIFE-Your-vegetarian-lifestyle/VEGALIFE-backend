package com.vegalife.repository.ai;

import com.vegalife.model.ai.AiMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

  /**
   * Newest-first page of conversation history for the model context (BR-AI-004, last 20). The id
   * tie-breaks rows sharing a created_at (user and assistant rows written in the same transaction
   * share NOW()). Reverse in the service to get chronological order for the prompt.
   */
  List<AiMessage> findTop20ByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId);
}
