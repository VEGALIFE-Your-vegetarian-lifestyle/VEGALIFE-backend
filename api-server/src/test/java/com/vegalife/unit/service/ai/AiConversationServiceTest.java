package com.vegalife.unit.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vegalife.dto.response.ai.ConversationDetailResponse;
import com.vegalife.dto.response.ai.ConversationSummaryResponse;
import com.vegalife.model.ai.AiConversation;
import com.vegalife.model.ai.AiMessage;
import com.vegalife.repository.ai.AiConversationRepository;
import com.vegalife.repository.ai.AiMessageRepository;
import com.vegalife.service.ai.AiConversationService;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiConversationServiceTest {

  @Mock private AiConversationRepository conversationRepository;
  @Mock private AiMessageRepository messageRepository;

  @InjectMocks private AiConversationService service;

  @Test
  void listConversations_mapsOwnRowsInRepositoryOrder() {
    UUID userId = UUID.randomUUID();
    Instant older = Instant.parse("2026-10-01T10:00:00Z");
    Instant newer = Instant.parse("2026-10-07T10:00:00Z");
    AiConversation first =
        AiConversation.builder().id(UUID.randomUUID()).userId(userId).title("newer chat").build();
    first.setUpdatedAt(newer);
    AiConversation second =
        AiConversation.builder().id(UUID.randomUUID()).userId(userId).title("older chat").build();
    second.setUpdatedAt(older);
    when(conversationRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId))
        .thenReturn(List.of(first, second));

    List<ConversationSummaryResponse> result = service.listConversations(userId);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo(first.getId());
    assertThat(result.get(0).getTitle()).isEqualTo("newer chat");
    assertThat(result.get(0).getUpdatedAt()).isEqualTo(newer);
    assertThat(result.get(1).getId()).isEqualTo(second.getId());
  }

  @Test
  void getConversation_whenOwned_returnsMessagesInRepositoryOrder() {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    AiConversation conversation =
        AiConversation.builder().id(conversationId).userId(userId).title("breakfast").build();
    conversation.setUpdatedAt(Instant.parse("2026-10-07T10:00:00Z"));
    when(conversationRepository.findByIdAndUserId(conversationId, userId))
        .thenReturn(Optional.of(conversation));
    AiMessage userMessage =
        AiMessage.builder()
            .conversationId(conversationId)
            .role(AiMessage.Role.user)
            .content("hi")
            .build();
    AiMessage assistantMessage =
        AiMessage.builder()
            .conversationId(conversationId)
            .role(AiMessage.Role.assistant)
            .content("hello")
            .build();
    when(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId))
        .thenReturn(List.of(userMessage, assistantMessage));

    ConversationDetailResponse result = service.getConversation(userId, conversationId);

    assertThat(result.getId()).isEqualTo(conversationId);
    assertThat(result.getTitle()).isEqualTo("breakfast");
    assertThat(result.getMessages()).hasSize(2);
    assertThat(result.getMessages().get(0).getRole()).isEqualTo(AiMessage.Role.user);
    assertThat(result.getMessages().get(1).getRole()).isEqualTo(AiMessage.Role.assistant);
  }

  @Test
  void getConversation_whenUnknownOrForeign_throwsNotFoundAndReadsNoMessages() {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    when(conversationRepository.findByIdAndUserId(conversationId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getConversation(userId, conversationId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Conversation not found");

    org.mockito.Mockito.verifyNoInteractions(messageRepository);
  }
}
