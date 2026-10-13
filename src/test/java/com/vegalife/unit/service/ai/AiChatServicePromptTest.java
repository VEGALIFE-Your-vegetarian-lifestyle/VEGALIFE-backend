package com.vegalife.unit.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.mapper.profile.ProfileMapper;
import com.vegalife.dto.request.ai.SendMessageRequest;
import com.vegalife.model.user.DietaryPreferences;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.ai.AiConversationRepository;
import com.vegalife.repository.ai.AiMessageRepository;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.service.ai.AiChatService;
import com.vegalife.service.ai.AiQuotaGuard;
import com.vegalife.service.ai.MenuTools;
import com.vegalife.service.menu.MenuQueryFilterResolver;
import com.vegalife.service.menu.MenuService;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

/**
 * Covers the content of the system prompt that {@code AiChatService} builds: the vegan/platform
 * scoping with refusal, the stored dietary profile with hard-vs-soft framing, and the derived BMI.
 * The prompt builder is private, so the prompt is observed the way the provider would see it —
 * captured from {@code spec.system(...)} on a real {@link #send} turn.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServicePromptTest {

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

    MenuTools menuTools =
        new MenuTools(
            mock(MenuService.class),
            new MenuQueryFilterResolver(),
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

  private void stubClient() {
    when(chatClientBuilder.defaultToolCallbacks(any(ToolCallback[].class)))
        .thenReturn(chatClientBuilder);
    when(chatClientBuilder.build()).thenReturn(chatClient);
    aiChatService.initChatClient();
    when(chatClient.prompt()).thenReturn(spec);
    when(spec.system(anyString())).thenReturn(spec);
    when(spec.messages(anyList())).thenReturn(spec);
    when(spec.toolContext(anyMap())).thenReturn(spec);
    when(spec.call()).thenReturn(callSpec);
    when(callSpec.content()).thenReturn("ok");
    when(quotaGuard.requireAllowance(userId)).thenReturn(window);
    when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            inv -> {
              // Guarded: re-stubbing (a second send in one test) re-invokes this with a null
              // callback during the when() call itself.
              TransactionCallback<?> callback = inv.getArgument(0);
              return callback == null
                  ? null
                  : callback.doInTransaction(mock(TransactionStatus.class));
            });
  }

  /**
   * Runs one turn for the given profile and returns the system prompt the provider would see. Some
   * tests send two turns against the same mocks, so the last captured prompt is the one under test.
   */
  private String systemPromptFor(UserProfile profile) {
    stubClient();
    when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.ofNullable(profile));

    aiChatService.send(userId, SendMessageRequest.builder().message("hi").build());

    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(spec, atLeastOnce()).system(prompt.capture());
    return prompt.getValue();
  }

  @Test
  void systemPrompt_scopesToVeganAndPlatformTopicsAndRefusesTheRest() {
    String prompt = systemPromptFor(null);

    assertThat(prompt).contains("vegan-lifestyle assistant").contains("plant-based");
    assertThat(prompt).contains("refuse").contains("non-vegan");
    // The old instruction to guess dietary data from the conversation is gone.
    assertThat(prompt).doesNotContain("Infer the member's dietary preferences");
  }

  @Test
  void systemPrompt_includesDietaryProfileWithHardAndSoftConstraints() {
    UserProfile profile =
        UserProfile.builder()
            .dietaryPreferences(
                DietaryPreferences.builder()
                    .preferences(List.of("spicy"))
                    .dislikes(List.of("mushrooms"))
                    .allergies(List.of("peanuts", "shellfish"))
                    .restrictions(List.of("gluten free"))
                    .healthGoal("weight loss")
                    .build())
            .build();

    String prompt = systemPromptFor(profile);

    assertThat(prompt).contains("Allergies (hard constraint").contains("peanuts, shellfish");
    assertThat(prompt).contains("Restrictions (hard constraint").contains("gluten free");
    assertThat(prompt).contains("Dislikes (avoid").contains("mushrooms");
    assertThat(prompt).contains("Preferences (lean into").contains("spicy");
    assertThat(prompt).contains("Health goal").contains("weight loss");
  }

  @Test
  void systemPrompt_omitsUnsetDietaryMembersAndAddsNothingForAFreshProfile() {
    // Only allergies set: the other four members must not appear at all.
    String partial =
        systemPromptFor(
            UserProfile.builder()
                .dietaryPreferences(
                    DietaryPreferences.builder().allergies(List.of("peanuts")).build())
                .build());

    assertThat(partial).contains("peanuts");
    assertThat(partial).doesNotContain("Dislikes");
    assertThat(partial).doesNotContain("Preferences (lean into");
    assertThat(partial).doesNotContain("Restrictions");
    assertThat(partial).doesNotContain("Health goal");

    // No dietary profile at all: no dietary block.
    String fresh = systemPromptFor(UserProfile.builder().age(25).build());
    assertThat(fresh).doesNotContain("Dietary profile");
    assertThat(fresh).contains("Age: 25");
  }

  @Test
  void systemPrompt_appendsDerivedBmiWhenHeightAndWeightAreKnown() {
    // 70kg / (1.70m)^2 = 24.2
    String prompt =
        systemPromptFor(
            UserProfile.builder()
                .heightCm(new BigDecimal("170.00"))
                .weightKg(new BigDecimal("70"))
                .build());

    assertThat(prompt).contains("BMI: 24.2").contains("not medical advice");
  }

  @Test
  void systemPrompt_omitsBmiWhenHeightOrWeightIsMissing() {
    String noWeight =
        systemPromptFor(UserProfile.builder().heightCm(new BigDecimal("170.00")).build());
    assertThat(noWeight).doesNotContain("BMI");

    String noHeight = systemPromptFor(UserProfile.builder().weightKg(new BigDecimal("70")).build());
    assertThat(noHeight).doesNotContain("BMI");
  }
}
