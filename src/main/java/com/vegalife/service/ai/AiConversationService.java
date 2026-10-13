package com.vegalife.service.ai;

import com.vegalife.dto.response.ai.ConversationDetailResponse;
import com.vegalife.dto.response.ai.ConversationMessageResponse;
import com.vegalife.dto.response.ai.ConversationSummaryResponse;
import com.vegalife.model.ai.AiConversation;
import com.vegalife.model.ai.AiMessage;
import com.vegalife.repository.ai.AiConversationRepository;
import com.vegalife.repository.ai.AiMessageRepository;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of AI chat (issue #115): list the caller's conversations and load one thread's
 * messages. Ownership is enforced on every read (BR-AI-003) — a conversation is visible only to its
 * owner.
 */
@Service
@RequiredArgsConstructor
public class AiConversationService {

  private final AiConversationRepository conversationRepository;
  private final AiMessageRepository messageRepository;

  /** The caller's conversations, most recently active first. */
  @Transactional(readOnly = true)
  public List<ConversationSummaryResponse> listConversations(UUID userId) {
    return conversationRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId).stream()
        .map(this::toSummary)
        .toList();
  }

  /**
   * One owned conversation with its full message history in chronological order. An unknown id and
   * another member's id are indistinguishable and both raise 404 (BR-AI-003), so the API never
   * confirms the existence of a conversation the caller does not own.
   */
  @Transactional(readOnly = true)
  public ConversationDetailResponse getConversation(UUID userId, UUID conversationId) {
    AiConversation conversation =
        conversationRepository
            .findByIdAndUserId(conversationId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

    List<ConversationMessageResponse> messages =
        messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId).stream()
            .map(this::toMessage)
            .toList();

    return ConversationDetailResponse.builder()
        .id(conversation.getId())
        .title(conversation.getTitle())
        .updatedAt(conversation.getUpdatedAt())
        .messages(messages)
        .build();
  }

  private ConversationSummaryResponse toSummary(AiConversation conversation) {
    return ConversationSummaryResponse.builder()
        .id(conversation.getId())
        .title(conversation.getTitle())
        .updatedAt(conversation.getUpdatedAt())
        .build();
  }

  private ConversationMessageResponse toMessage(AiMessage message) {
    return ConversationMessageResponse.builder()
        .role(message.getRole())
        .content(message.getContent())
        .createdAt(message.getCreatedAt())
        .build();
  }
}
