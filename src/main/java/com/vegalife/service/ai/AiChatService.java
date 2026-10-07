package com.vegalife.service.ai;

import com.vegalife.dto.request.ai.SendMessageRequest;
import com.vegalife.dto.response.ai.AiUsageQuotaResponse;
import com.vegalife.dto.response.ai.SendMessageResponse;
import com.vegalife.model.ai.AiConversation;
import com.vegalife.model.ai.AiMessage;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.ai.AiConversationRepository;
import com.vegalife.repository.ai.AiMessageRepository;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.shared.exception.AiProviderException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/**
 * Runs one AI chat turn: verify ownership, enforce the monthly quota (BR-AI-001), build the
 * profile-plus-history context (BR-AI-004), call the provider, and — only on success — persist both
 * messages and count the request (BR-AI-002). Both the JSON and the SSE endpoint share this service
 * (NFR-MAINT-001).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

  private static final int MAX_CONTEXT_MESSAGES = 20;
  private static final int MAX_TITLE_LENGTH = 60;
  private static final long SSE_TIMEOUT_MILLIS = 180_000L;

  private final ChatClient.Builder chatClientBuilder;
  private final AiConversationRepository conversationRepository;
  private final AiMessageRepository messageRepository;
  private final UserProfileRepository userProfileRepository;
  private final AiQuotaGuard quotaGuard;
  private final TransactionTemplate transactionTemplate;

  private ChatClient chatClient;

  /** Built once at startup so the streaming path never races to initialise a shared field. */
  @PostConstruct
  public void initChatClient() {
    this.chatClient = chatClientBuilder.build();
  }

  /** Blocking turn for {@code POST /api/ai/messages}. */
  public SendMessageResponse send(UUID userId, SendMessageRequest request) {
    Optional<AiConversation> existing = resolveConversation(userId, request.getConversationId());
    AiQuotaGuard.QuotaWindow window = quotaGuard.requireAllowance(userId);

    String reply;
    try {
      reply =
          chatClient
              .prompt()
              .system(buildSystemPrompt(userId))
              .messages(buildContext(existing, request.getMessage()))
              .call()
              .content();
    } catch (RuntimeException ex) {
      log.error("AI provider call failed for user {}", userId, ex);
      throw new AiProviderException("AI provider request failed", ex);
    }

    if (reply == null || reply.isBlank()) {
      throw new AiProviderException("AI provider request failed");
    }

    PersistedTurn persisted = persistTurn(userId, existing, request.getMessage(), reply, window);
    return SendMessageResponse.builder()
        .conversationId(persisted.conversationId())
        .reply(reply)
        .usage(toQuotaResponse(window))
        .build();
  }

  /**
   * Streaming turn for {@code POST /api/ai/messages/stream}. Ownership, quota and validation errors
   * are raised before the emitter is returned so they still produce a JSON error response; a
   * failure after the stream opens is delivered as an {@code error} event.
   */
  public SseEmitter stream(UUID userId, SendMessageRequest request) {
    Optional<AiConversation> existing = resolveConversation(userId, request.getConversationId());
    AiQuotaGuard.QuotaWindow window = quotaGuard.requireAllowance(userId);
    List<Message> context = buildContext(existing, request.getMessage());
    String systemPrompt = buildSystemPrompt(userId);

    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
    StringBuilder fullReply = new StringBuilder();

    Disposable subscription =
        chatClient.prompt().system(systemPrompt).messages(context).stream()
            .content()
            .subscribe(
                delta -> {
                  fullReply.append(delta);
                  sendEvent(emitter, "delta", Map.of("reply", delta));
                },
                error -> {
                  log.error("AI provider stream failed for user {}", userId, error);
                  failStream(emitter);
                },
                () ->
                    completeStream(
                        emitter, fullReply.toString(), userId, existing, request, window));

    emitter.onCompletion(subscription::dispose);
    emitter.onTimeout(
        () -> {
          subscription.dispose();
          emitter.complete();
        });
    return emitter;
  }

  /**
   * Terminal success branch of a stream: persist the turn and emit {@code done}, or emit {@code
   * error} when the reply is empty or persistence fails. The quota is counted only here, so a
   * stream that dies before completion consumes nothing (BR-AI-002).
   */
  private void completeStream(
      SseEmitter emitter,
      String reply,
      UUID userId,
      Optional<AiConversation> existing,
      SendMessageRequest request,
      AiQuotaGuard.QuotaWindow window) {
    if (reply.isBlank()) {
      failStream(emitter);
      return;
    }
    try {
      PersistedTurn persisted = persistTurn(userId, existing, request.getMessage(), reply, window);
      Map<String, Object> done = new LinkedHashMap<>();
      done.put("conversationId", persisted.conversationId());
      done.put("reply", reply);
      done.put("usage", toQuotaResponse(window));
      sendEvent(emitter, "done", done);
      emitter.complete();
    } catch (RuntimeException ex) {
      log.error("Failed to persist AI turn for user {}", userId, ex);
      failStream(emitter);
    }
  }

  private void failStream(SseEmitter emitter) {
    sendEvent(emitter, "error", Map.of("message", "AI provider request failed"));
    emitter.complete();
  }

  /**
   * Loads the supplied conversation and asserts ownership (BR-AI-003); an unknown or foreign id is
   * indistinguishable and yields 404. An absent id means "start a new conversation" and returns
   * empty.
   */
  private Optional<AiConversation> resolveConversation(UUID userId, UUID conversationId) {
    if (conversationId == null) {
      return Optional.empty();
    }
    return Optional.of(
        conversationRepository
            .findByIdAndUserId(conversationId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found")));
  }

  /** System prompt: persona plus whatever profile facts are known (BR-AI-004). */
  private String buildSystemPrompt(UUID userId) {
    UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);

    StringBuilder prompt = new StringBuilder();
    prompt.append(
        "You are Vegalife's vegan-lifestyle assistant. Help members with plant-based "
            + "cooking, nutrition, recipes, and sustainable living. Keep answers practical "
            + "and concise.\n\n");
    prompt.append(
        "Infer the member's dietary preferences, restrictions, and recent activity "
            + "from the conversation instead of asking for data you can reasonably guess.\n");

    if (profile != null) {
      prompt.append("\nMember profile:\n");
      if (profile.getAge() != null) {
        prompt.append("- Age: ").append(profile.getAge()).append('\n');
      }
      if (profile.getGender() != null) {
        prompt.append("- Gender: ").append(profile.getGender()).append('\n');
      }
      if (profile.getHeightCm() != null) {
        prompt.append("- Height: ").append(profile.getHeightCm()).append(" cm\n");
      }
      if (profile.getWeightKg() != null) {
        prompt.append("- Weight: ").append(profile.getWeightKg()).append(" kg\n");
      }
      if (profile.getDescription() != null && !profile.getDescription().isBlank()) {
        prompt.append("- About: ").append(profile.getDescription()).append('\n');
      }
    }
    return prompt.toString();
  }

  /**
   * Prior conversation history (chronological, capped at the last {@value #MAX_CONTEXT_MESSAGES})
   * followed by the new user message.
   */
  private List<Message> buildContext(Optional<AiConversation> conversation, String userText) {
    List<Message> messages = new ArrayList<>();
    conversation.ifPresent(
        conv -> {
          List<AiMessage> history =
              messageRepository.findTop20ByConversationIdOrderByCreatedAtDescIdDesc(conv.getId());
          for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage message = history.get(i);
            messages.add(toProviderMessage(message));
          }
        });
    messages.add(new UserMessage(userText));
    return messages;
  }

  private Message toProviderMessage(AiMessage message) {
    return switch (message.getRole()) {
      case assistant -> new AssistantMessage(message.getContent());
      // No system rows are persisted (the system prompt is passed via .system()), but map the role
      // explicitly so a future "persist system messages" change cannot silently mislabel one.
      case system -> new SystemMessage(message.getContent());
      case user -> new UserMessage(message.getContent());
    };
  }

  /**
   * Persists the whole successful turn in one transaction (FR-004, FR-008): create the conversation
   * when new, write both messages, refresh {@code updated_at}, and count the request against the
   * checked window.
   */
  private PersistedTurn persistTurn(
      UUID userId,
      Optional<AiConversation> existing,
      String userText,
      String reply,
      AiQuotaGuard.QuotaWindow window) {

    PersistedTurn result =
        transactionTemplate.execute(
            status -> {
              Instant now = Instant.now();
              AiConversation conversation =
                  existing.orElseGet(
                      () ->
                          conversationRepository.save(
                              AiConversation.builder()
                                  .userId(userId)
                                  .title(buildTitle(userText))
                                  .build()));
              conversation.setUpdatedAt(now);
              conversationRepository.save(conversation);

              messageRepository.save(
                  AiMessage.builder()
                      .conversationId(conversation.getId())
                      .role(AiMessage.Role.user)
                      .content(userText)
                      .build());
              messageRepository.save(
                  AiMessage.builder()
                      .conversationId(conversation.getId())
                      .role(AiMessage.Role.assistant)
                      .content(reply)
                      .build());

              quotaGuard.record(userId, window);
              return new PersistedTurn(conversation.getId());
            });

    if (result == null) {
      throw new AiProviderException("AI provider request failed");
    }
    return result;
  }

  private String buildTitle(String userText) {
    String title = userText.strip();
    return title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH);
  }

  private AiUsageQuotaResponse toQuotaResponse(AiQuotaGuard.QuotaWindow window) {
    return AiUsageQuotaResponse.builder()
        .used(window.used() + 1)
        .limit(window.limit())
        .windowStart(window.start())
        .windowEnd(window.end())
        .build();
  }

  private void sendEvent(SseEmitter emitter, String name, Object payload) {
    try {
      emitter.send(SseEmitter.event().name(name).data(payload));
    } catch (Exception ex) {
      log.debug("SSE client disconnected before '{}' event could be delivered", name);
    }
  }

  private record PersistedTurn(UUID conversationId) {}
}
