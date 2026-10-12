package com.vegalife.unit.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.ai.SendMessageRequest;
import com.vegalife.dto.response.ai.SendMessageResponse;
import com.vegalife.model.ai.AiConversation;
import com.vegalife.repository.ai.AiConversationRepository;
import com.vegalife.repository.ai.AiMessageRepository;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.service.ai.AiChatService;
import com.vegalife.service.ai.AiQuotaGuard;
import com.vegalife.service.ai.AiToolContext;
import java.time.Instant;
import java.util.Map;
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
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

/**
 * Tool-calling wiring for the AI chat with mocked MCP callbacks (ADR-012): the menu tools are
 * attached at startup, the caller's JWT reaches the ToolContext on both the blocking and the
 * streaming path, and the system prompt guides the model (confirm before scheduling) without ever
 * containing the token itself.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceToolTest {

  @Mock private ChatClient.Builder chatClientBuilder;
  @Mock private AiConversationRepository conversationRepository;
  @Mock private AiMessageRepository messageRepository;
  @Mock private UserProfileRepository userProfileRepository;
  @Mock private AiQuotaGuard quotaGuard;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private AsyncMcpToolCallbackProvider menuToolProvider;

  @InjectMocks private AiChatService aiChatService;

  private ChatClient chatClient;
  private ChatClient.ChatClientRequestSpec spec;
  private ChatClient.CallResponseSpec callSpec;
  private ChatClient.StreamResponseSpec streamSpec;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    chatClient = mock(ChatClient.class);
    spec = mock(ChatClient.ChatClientRequestSpec.class);
    callSpec = mock(ChatClient.CallResponseSpec.class);
    streamSpec = mock(ChatClient.StreamResponseSpec.class);
  }

  /** Builds the chat client with no tools (degraded mode) and stubs the shared prompt chain. */
  private void stubToollessClient() {
    when(menuToolProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
    when(chatClientBuilder.defaultToolCallbacks(new ToolCallback[0])).thenReturn(chatClientBuilder);
    when(chatClientBuilder.build()).thenReturn(chatClient);
    aiChatService.initChatClient();
    when(chatClient.prompt()).thenReturn(spec);
    when(spec.system(anyString())).thenReturn(spec);
    when(spec.messages(anyList())).thenReturn(spec);
    when(spec.toolContext(anyMap())).thenReturn(spec);
  }

  private void stubBlockingCall(String reply) {
    when(spec.call()).thenReturn(callSpec);
    when(callSpec.content()).thenReturn(reply);
  }

  private void stubQuotaAndPersistence() {
    when(quotaGuard.requireAllowance(userId))
        .thenReturn(
            new AiQuotaGuard.QuotaWindow(
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"),
                20,
                1L));
    when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(conversationRepository.save(any(AiConversation.class)))
        .thenAnswer(
            inv -> {
              AiConversation c = inv.getArgument(0);
              c.setId(UUID.randomUUID());
              return c;
            });
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            inv ->
                ((TransactionCallback<?>) inv.getArgument(0))
                    .doInTransaction(mock(TransactionStatus.class)));
  }

  @Test
  void initChatClient_attachesEveryMenuToolFromProvider() {
    ToolCallback get = mock(ToolCallback.class);
    ToolCallback create = mock(ToolCallback.class);
    ToolCallback update = mock(ToolCallback.class);
    ToolCallback schedule = mock(ToolCallback.class);
    when(menuToolProvider.getToolCallbacks())
        .thenReturn(new ToolCallback[] {get, create, update, schedule});
    when(chatClientBuilder.defaultToolCallbacks(get, create, update, schedule))
        .thenReturn(chatClientBuilder);
    when(chatClientBuilder.build()).thenReturn(chatClient);

    aiChatService.initChatClient();

    verify(chatClientBuilder).defaultToolCallbacks(get, create, update, schedule);
  }

  @Test
  void send_withoutProviderTools_stillAnswersTheTurn() {
    stubToollessClient();
    stubQuotaAndPersistence();
    stubBlockingCall("plain reply");

    SendMessageResponse response =
        aiChatService.send(userId, "jwt-x", SendMessageRequest.builder().message("hi").build());

    assertThat(response.getReply()).isEqualTo("plain reply");
  }

  @Test
  void send_promptGuidesMenuToolsAndNeverContainsTheJwt() {
    stubToollessClient();
    stubQuotaAndPersistence();
    stubBlockingCall("ok");

    aiChatService.send(
        userId, "secret-jwt-value", SendMessageRequest.builder().message("hi").build());

    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(spec).system(prompt.capture());
    assertThat(prompt.getValue())
        .contains("get_menu", "create_menu", "update_menu", "schedule_menu")
        .contains("confirm the exact week first")
        .contains("never invent menu data");
    assertThat(prompt.getValue()).doesNotContain("secret-jwt-value");
  }

  @Test
  void stream_passesCallerJwtAsToolContext() {
    stubToollessClient();
    when(quotaGuard.requireAllowance(userId))
        .thenReturn(
            new AiQuotaGuard.QuotaWindow(
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"),
                20,
                1L));
    when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(spec.stream()).thenReturn(streamSpec);
    when(streamSpec.content()).thenReturn(Flux.empty());

    aiChatService.stream(userId, "stream-jwt", SendMessageRequest.builder().message("hi").build());

    ArgumentCaptor<Map<String, Object>> toolContext = ArgumentCaptor.forClass(Map.class);
    verify(spec).toolContext(toolContext.capture());
    assertThat(toolContext.getValue())
        .containsExactly(Map.entry(AiToolContext.CALLER_JWT_KEY, "stream-jwt"));
  }
}
