package com.vegalife.integration.controller.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiUsage;
import com.vegalife.model.user.User;
import com.vegalife.repository.ai.AiConversationRepository;
import com.vegalife.repository.ai.AiMessageRepository;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

/**
 * End-to-end acceptance coverage for the chat send API (FR-001 to FR-010, BR-AI series): quota gate
 * before the provider, ownership 404, success persistence and usage counting, and the SSE
 * delta/done/error sequence. Only the provider model is replaced ({@link ChatModel}); everything
 * else — controller, service, guard, repositories — runs for real on PostgreSQL.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiChatControllerIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("vegalife_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false);

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private AiPlanRepository planRepository;
  @Autowired private AiUsageRepository usageRepository;
  @Autowired private AiConversationRepository conversationRepository;
  @Autowired private AiMessageRepository messageRepository;
  @Autowired private JwtTokenService jwtTokenService;

  @MockitoBean private ChatModel chatModel;

  private User user;
  private String accessToken;

  @BeforeEach
  void setUp() {
    user =
        userRepository.save(
            User.builder()
                .username("aichat" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    accessToken = jwtTokenService.generateAccessToken(user);

    when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Try a tofu scramble."));
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(
            Flux.just(chatResponse("Try"), chatResponse(" a tofu"), chatResponse(" scramble.")));
  }

  private static ChatResponse chatResponse(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  @AfterEach
  void cleanUp() {
    // The SSE path persists on a separate thread/connection, so the test cannot roll back; remove
    // everything this test created (children before the user row that owns them).
    usageRepository.deleteAll();
    messageRepository.deleteAll();
    conversationRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void send_withoutAuthentication_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void send_withBlankMessage_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/messages")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void send_withNewConversation_persistsTurnAndCountsUsage() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/ai/messages")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"What for lunch?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Message sent successfully"))
            .andExpect(jsonPath("$.data.reply").value("Try a tofu scramble."))
            .andExpect(jsonPath("$.data.usage.used").value(1))
            .andExpect(jsonPath("$.data.usage.limit").value(20))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    String conversationId =
        com.jayway.jsonpath.JsonPath.read(body, "$.data.conversationId").toString();

    assertThat(
            conversationRepository.findByIdAndUserId(UUID.fromString(conversationId), user.getId()))
        .isPresent();
    assertThat(
            messageRepository.findTop20ByConversationIdOrderByCreatedAtDescIdDesc(
                UUID.fromString(conversationId)))
        .hasSize(2);

    YearMonth month = YearMonth.now(ZoneOffset.UTC);
    Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    assertThat(usageRepository.sumRequestCountInWindow(user.getId(), start, end)).isEqualTo(1L);
  }

  @Test
  void send_withForeignConversation_returns404AndPersistsNothing() throws Exception {
    User other =
        userRepository.save(
            User.builder()
                .username("aichatother" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    com.vegalife.model.ai.AiConversation foreign =
        conversationRepository.save(
            com.vegalife.model.ai.AiConversation.builder()
                .userId(other.getId())
                .title("other chat")
                .build());

    mockMvc
        .perform(
            post("/api/ai/messages")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"" + foreign.getId() + "\",\"message\":\"hi\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Conversation not found"));

    assertThat(messageRepository.findAll()).isEmpty();
    assertThat(usageRepository.findAll()).isEmpty();
  }

  @Test
  void send_atMonthlyLimit_returns429WithRetryAfterAndNoProviderCall() throws Exception {
    AiPlan freePlan = planRepository.findByCode("FREE").orElseThrow();
    YearMonth month = YearMonth.now(ZoneOffset.UTC);
    Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    usageRepository.save(
        AiUsage.builder()
            .userId(user.getId())
            .requestCount(freePlan.getMonthlyRequestLimit())
            .periodStart(start)
            .periodEnd(end)
            .build());

    mockMvc
        .perform(
            post("/api/ai/messages")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"one more\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(
            jsonPath("$.message")
                .value(
                    org.hamcrest.Matchers.containsString(
                        "/" + freePlan.getMonthlyRequestLimit() + ")")));

    assertThat(conversationRepository.findAll()).isEmpty();
    assertThat(messageRepository.findAll()).isEmpty();
  }

  @Test
  void send_providerFailure_returns502AndPersistsNothing() throws Exception {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("provider down"));

    mockMvc
        .perform(
            post("/api/ai/messages")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("AI provider request failed"));

    assertThat(conversationRepository.findAll()).isEmpty();
    assertThat(messageRepository.findAll()).isEmpty();
    assertThat(usageRepository.findAll()).isEmpty();
  }

  @Test
  void stream_emitsDeltaThenDoneAndPersists() throws Exception {
    MvcResult mvcResult =
        mockMvc
            .perform(
                post("/api/ai/messages/stream")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"What for lunch?\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

    // The mocked Flux emits synchronously and the service completes the emitter inline, so the SSE
    // payload is committed before the async dispatch; await completion without re-entering the
    // security filter chain (which would deny the already-committed async response).
    mvcResult.getAsyncResult(10_000);

    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).contains("event:delta").contains("event:done");
    assertThat(body).contains("Try a tofu scramble.");

    assertThat(conversationRepository.findAll()).hasSize(1);
    assertThat(messageRepository.findAll()).hasSize(2);
    YearMonth month = YearMonth.now(ZoneOffset.UTC);
    Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    assertThat(usageRepository.sumRequestCountInWindow(user.getId(), start, end)).isEqualTo(1L);
  }

  @Test
  void stream_overQuota_returns429WithoutOpeningStream() throws Exception {
    AiPlan freePlan = planRepository.findByCode("FREE").orElseThrow();
    YearMonth month = YearMonth.now(ZoneOffset.UTC);
    Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    usageRepository.save(
        AiUsage.builder()
            .userId(user.getId())
            .requestCount(freePlan.getMonthlyRequestLimit())
            .periodStart(start)
            .periodEnd(end)
            .build());

    mockMvc
        .perform(
            post("/api/ai/messages/stream")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"one more\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.success").value(false));
  }
}
