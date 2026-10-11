package com.vegalife.unit.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.ai.SendMessageRequest;
import com.vegalife.dto.response.ai.SendMessageResponse;
import com.vegalife.model.ai.AiConversation;
import com.vegalife.model.ai.AiMessage;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.ai.AiConversationRepository;
import com.vegalife.repository.ai.AiMessageRepository;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.service.ai.AiChatService;
import com.vegalife.service.ai.AiQuotaGuard;
import com.vegalife.shared.exception.AiProviderException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

  @Mock private ChatClient.Builder chatClientBuilder;
  @Mock private AiConversationRepository conversationRepository;
  @Mock private AiMessageRepository messageRepository;
  @Mock private UserProfileRepository userProfileRepository;
  @Mock private AiQuotaGuard quotaGuard;
  @Mock private TransactionTemplate transactionTemplate;

  @InjectMocks private AiChatService aiChatService;

  private ChatClient chatClient;
  private ChatClient.ChatClientRequestSpec spec;
  private ChatClient.CallResponseSpec callSpec;

  private UUID userId;
  private AiQuotaGuard.QuotaWindow window;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    window =
        new AiQuotaGuard.QuotaWindow(
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), 20, 2L);

    chatClient = mock(ChatClient.class);
    spec = mock(ChatClient.ChatClientRequestSpec.class);
    callSpec = mock(ChatClient.CallResponseSpec.class);
  }

  private void stubClient() {
    when(chatClientBuilder.build()).thenReturn(chatClient);
    // @InjectMocks does not run @PostConstruct, so build the client the way startup would.
    aiChatService.initChatClient();
    when(chatClient.prompt()).thenReturn(spec);
    when(spec.system(anyString())).thenReturn(spec);
    when(spec.messages(anyList())).thenReturn(spec);
    when(spec.call()).thenReturn(callSpec);
  }

  private void stubTransactionTemplate() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0))
                    .doInTransaction(mock(TransactionStatus.class)));
  }

  @Test
  void send_newConversation_persistsBothMessagesAndCountsUsage() {
    stubClient();
    stubTransactionTemplate();
    when(quotaGuard.requireAllowance(userId)).thenReturn(window);
    when(callSpec.content()).thenReturn("Try a tofu scramble.");
    when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(conversationRepository.save(any(AiConversation.class)))
        .thenAnswer(
            inv -> {
              AiConversation c = inv.getArgument(0);
              if (c.getId() == null) {
                c.setId(UUID.randomUUID());
              }
              return c;
            });

    SendMessageResponse response =
        aiChatService.send(userId, SendMessageRequest.builder().message("What for lunch?").build());

    assertThat(response.getReply()).isEqualTo("Try a tofu scramble.");
    assertThat(response.getConversationId()).isNotNull();
    assertThat(response.getUsage().getUsed()).isEqualTo(3L);
    assertThat(response.getUsage().getLimit()).isEqualTo(20);

    ArgumentCaptor<AiMessage> saved = ArgumentCaptor.forClass(AiMessage.class);
    verify(messageRepository, org.mockito.Mockito.times(2)).save(saved.capture());
    assertThat(saved.getAllValues())
        .extracting(AiMessage::getRole)
        .containsExactly(AiMessage.Role.user, AiMessage.Role.assistant);
    verify(quotaGuard).record(eq(userId), eq(window));
  }

  @Test
  void send_truncatesConversationTitleToSixtyChars() {
    stubClient();
    stubTransactionTemplate();
    when(quotaGuard.requireAllowance(userId)).thenReturn(window);
    when(callSpec.content()).thenReturn("ok");
    when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(conversationRepository.save(any(AiConversation.class)))
        .thenAnswer(
            inv -> {
              AiConversation c = inv.getArgument(0);
              c.setId(UUID.randomUUID());
              return c;
            });
    String longMessage = "x".repeat(100);

    aiChatService.send(userId, SendMessageRequest.builder().message(longMessage).build());

    ArgumentCaptor<AiConversation> captor = ArgumentCaptor.forClass(AiConversation.class);
    verify(conversationRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    assertThat(captor.getAllValues().get(0).getTitle()).hasSize(60);
  }

  @Test
  void send_unknownConversation_throws404WithoutCallingProviderOrRecording() {
    UUID conversationId = UUID.randomUUID();
    when(conversationRepository.findByIdAndUserId(conversationId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                aiChatService.send(
                    userId,
                    SendMessageRequest.builder()
                        .message("hi")
                        .conversationId(conversationId)
                        .build()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Conversation not found");

    verify(quotaGuard, never()).requireAllowance(any());
    verify(chatClient, never()).prompt();
  }

  @Test
  void send_providerReturnsBlank_throws502AndPersistsNothing() {
    stubClient();
    when(quotaGuard.requireAllowance(userId)).thenReturn(window);
    when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(callSpec.content()).thenReturn("  ");

    assertThatThrownBy(
            () -> aiChatService.send(userId, SendMessageRequest.builder().message("hi").build()))
        .isInstanceOf(AiProviderException.class);

    verify(conversationRepository, never()).save(any());
    verify(messageRepository, never()).save(any());
    verify(quotaGuard, never()).record(any(), any());
  }

  @Test
  void send_existingConversation_buildsHistoryChronologicallyWithProfileSystemPrompt() {
    stubClient();
    stubTransactionTemplate();
    UUID conversationId = UUID.randomUUID();
    Instant base = Instant.parse("2026-10-05T10:00:00Z");
    AiConversation conversation =
        AiConversation.builder().id(conversationId).userId(userId).title("lunch").build();
    when(conversationRepository.findByIdAndUserId(conversationId, userId))
        .thenReturn(Optional.of(conversation));
    // Repository returns newest-first; service must reverse to chronological order.
    when(messageRepository.findTop20ByConversationIdOrderByCreatedAtDescIdDesc(conversationId))
        .thenReturn(
            List.of(
                AiMessage.builder()
                    .conversationId(conversationId)
                    .role(AiMessage.Role.assistant)
                    .content("earlier reply")
                    .createdAt(base.plusSeconds(1))
                    .build(),
                AiMessage.builder()
                    .conversationId(conversationId)
                    .role(AiMessage.Role.user)
                    .content("earlier question")
                    .createdAt(base)
                    .build()));
    when(userProfileRepository.findByUserId(userId))
        .thenReturn(Optional.of(UserProfile.builder().age(30).description("vegan runner").build()));
    when(quotaGuard.requireAllowance(userId)).thenReturn(window);
    when(callSpec.content()).thenReturn("reply");
    when(conversationRepository.save(any(AiConversation.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    aiChatService.send(
        userId,
        SendMessageRequest.builder()
            .message("new question")
            .conversationId(conversationId)
            .build());

    ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
    verify(spec).system(systemPrompt.capture());
    assertThat(systemPrompt.getValue()).contains("Age: 30").contains("vegan runner");

    ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
    verify(spec).messages(messages.capture());
    assertThat(messages.getValue()).hasSize(3);
    assertThat(messages.getValue().get(0)).isInstanceOf(UserMessage.class);
    assertThat(messages.getValue().get(0).getText()).isEqualTo("earlier question");
    assertThat(messages.getValue().get(1)).isInstanceOf(AssistantMessage.class);
    assertThat(messages.getValue().get(2).getText()).isEqualTo("new question");
  }
}
