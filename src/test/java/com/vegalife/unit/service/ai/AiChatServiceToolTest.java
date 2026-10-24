package com.vegalife.unit.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.mapper.profile.ProfileMapper;
import com.vegalife.dto.request.ai.SendMessageRequest;
import com.vegalife.dto.response.ai.SendMessageResponse;
import com.vegalife.model.ai.AiConversation;
import com.vegalife.repository.ai.AiConversationRepository;
import com.vegalife.repository.ai.AiMessageRepository;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.service.ai.AiChatService;
import com.vegalife.service.ai.AiQuotaGuard;
import com.vegalife.service.ai.AiToolContext;
import com.vegalife.service.ai.MenuTools;
import com.vegalife.service.menu.MenuQueryFilterResolver;
import com.vegalife.service.menu.MenuService;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

/**
 * Tool-calling wiring for the AI chat now that the menu tools are served in-process (ADR-013): all
 * five are attached at startup — there is no longer a degraded "no MCP server reachable" mode — the
 * authenticated user id reaches the ToolContext on both the blocking and the streaming path, and
 * the system prompt guides the model (confirm before scheduling) without ever carrying that
 * identity.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceToolTest {

  private static final List<String> MENU_TOOL_NAMES =
      List.of(
          "get_menu",
          "get_meal_plan",
          "create_menu",
          "update_menu",
          "schedule_menu",
          "suggest_dishes");

  @Mock private ChatClient.Builder chatClientBuilder;
  @Mock private AiConversationRepository conversationRepository;
  @Mock private AiMessageRepository messageRepository;
  @Mock private UserProfileRepository userProfileRepository;
  @Mock private AiQuotaGuard quotaGuard;
  @Mock private TransactionTemplate transactionTemplate;

  private AiChatService aiChatService;

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

    // A real MenuTools, because MethodToolCallbackProvider reflects over the @Tool methods of the
    // object it is handed and a mock would report none. Its collaborators stay mocked: this test is
    // about wiring, not about menu behaviour (MenuToolsTest covers that).
    MenuTools menuTools =
        new MenuTools(
            mock(MenuService.class),
            new MenuQueryFilterResolver(),
            mock(com.vegalife.service.recipe.DishSuggestionFacade.class),
            Validation.buildDefaultValidatorFactory().getValidator(),
            new ObjectMapper());

    aiChatService =
        new AiChatService(
            chatClientBuilder,
            conversationRepository,
            messageRepository,
            userProfileRepository,
            quotaGuard,
            transactionTemplate,
            menuTools,
            ProfileMapper.INSTANCE);
  }

  private void stubChatClientAndInit() {
    when(chatClientBuilder.defaultToolCallbacks(any(ToolCallback[].class)))
        .thenReturn(chatClientBuilder);
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
  void initChatClient_attachesAllSixLocalTools() {
    when(chatClientBuilder.defaultToolCallbacks(any(ToolCallback[].class)))
        .thenReturn(chatClientBuilder);
    when(chatClientBuilder.build()).thenReturn(chatClient);

    aiChatService.initChatClient();

    ArgumentCaptor<ToolCallback[]> attached = ArgumentCaptor.forClass(ToolCallback[].class);
    verify(chatClientBuilder).defaultToolCallbacks(attached.capture());
    List<String> attachedNames =
        Arrays.stream(attached.getValue())
            .map(callback -> callback.getToolDefinition().name())
            .collect(Collectors.toList());
    assertThat(attachedNames).containsExactlyInAnyOrderElementsOf(MENU_TOOL_NAMES);
  }

  @Test
  void send_answersTheTurn() {
    stubChatClientAndInit();
    stubQuotaAndPersistence();
    stubBlockingCall("plain reply");

    SendMessageResponse response =
        aiChatService.send(userId, SendMessageRequest.builder().message("hi").build());

    assertThat(response.getReply()).isEqualTo("plain reply");
  }

  @Test
  void send_promptGuidesTheMenuToolsAndNeverCarriesTheCallerIdentity() {
    stubChatClientAndInit();
    stubQuotaAndPersistence();
    stubBlockingCall("ok");

    aiChatService.send(userId, SendMessageRequest.builder().message("hi").build());

    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(spec).system(prompt.capture());
    assertThat(prompt.getValue())
        .contains("get_menu", "create_menu", "update_menu", "schedule_menu")
        .contains("confirm the exact week first")
        .contains("never invent menu data");
    assertThat(prompt.getValue()).doesNotContain(userId.toString());
  }

  @Test
  void stream_carriesTheCallerUserIdAsToolContext() {
    stubChatClientAndInit();
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

    aiChatService.stream(userId, SendMessageRequest.builder().message("hi").build());

    ArgumentCaptor<Map<String, Object>> toolContext = ArgumentCaptor.forClass(Map.class);
    verify(spec).toolContext(toolContext.capture());
    assertThat(toolContext.getValue())
        .containsExactly(Map.entry(AiToolContext.USER_ID_KEY, userId));
  }
}
