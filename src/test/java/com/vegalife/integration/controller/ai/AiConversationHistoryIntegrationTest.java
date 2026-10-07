package com.vegalife.integration.controller.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.ai.AiConversation;
import com.vegalife.model.ai.AiMessage;
import com.vegalife.model.user.User;
import com.vegalife.repository.ai.AiConversationRepository;
import com.vegalife.repository.ai.AiMessageRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end acceptance coverage for the conversation history read APIs (issue #115): own-list
 * ordering, chronological messages, cross-user rejection with no leakage, 404 for unknown ids, and
 * 401 when unauthenticated. Only the provider model is irrelevant here; the controller, service and
 * repositories run for real on PostgreSQL.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiConversationHistoryIntegrationTest {

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
  @Autowired private AiConversationRepository conversationRepository;
  @Autowired private AiMessageRepository messageRepository;
  @Autowired private JwtTokenService jwtTokenService;

  private User user;
  private User other;
  private String accessToken;

  @BeforeEach
  void setUp() {
    user = saveUser("history");
    other = saveUser("historyother");
    accessToken = jwtTokenService.generateAccessToken(user);
  }

  private User saveUser(String prefix) {
    return userRepository.save(
        User.builder()
            .username(prefix + UUID.randomUUID())
            .email(UUID.randomUUID() + "@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build());
  }

  private AiConversation saveConversation(User owner, String title, Instant updatedAt) {
    AiConversation conversation =
        conversationRepository.save(
            AiConversation.builder().userId(owner.getId()).title(title).build());
    conversation.setUpdatedAt(updatedAt);
    return conversationRepository.save(conversation);
  }

  private AiMessage saveMessage(AiConversation conversation, AiMessage.Role role, String content) {
    return messageRepository.save(
        AiMessage.builder()
            .conversationId(conversation.getId())
            .role(role)
            .content(content)
            .build());
  }

  @AfterEach
  void cleanUp() {
    messageRepository.deleteAll();
    conversationRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void listConversations_returnsOnlyOwnNewestFirst() throws Exception {
    AiConversation older =
        saveConversation(user, "older chat", Instant.parse("2026-10-01T10:00:00Z"));
    AiConversation newer =
        saveConversation(user, "newer chat", Instant.parse("2026-10-07T10:00:00Z"));
    saveConversation(other, "someone else's chat", Instant.parse("2026-10-08T10:00:00Z"));

    mockMvc
        .perform(get("/api/ai/conversations").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id").value(newer.getId().toString()))
        .andExpect(jsonPath("$.data[0].title").value("newer chat"))
        .andExpect(jsonPath("$.data[1].id").value(older.getId().toString()));
  }

  @Test
  void getConversation_returnsMessagesChronologically() throws Exception {
    AiConversation conversation = saveConversation(user, "breakfast", Instant.now());
    saveMessage(conversation, AiMessage.Role.user, "What for breakfast?");
    saveMessage(conversation, AiMessage.Role.assistant, "A tofu scramble.");

    mockMvc
        .perform(
            get("/api/ai/conversations/" + conversation.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(conversation.getId().toString()))
        .andExpect(jsonPath("$.data.title").value("breakfast"))
        .andExpect(jsonPath("$.data.messages.length()").value(2))
        .andExpect(jsonPath("$.data.messages[0].role").value("user"))
        .andExpect(jsonPath("$.data.messages[0].content").value("What for breakfast?"))
        .andExpect(jsonPath("$.data.messages[1].role").value("assistant"));
  }

  @Test
  void getConversation_forAnotherUsersConversation_returns404WithoutLeaking() throws Exception {
    AiConversation foreign = saveConversation(other, "private", Instant.now());
    saveMessage(foreign, AiMessage.Role.user, "secret");

    mockMvc
        .perform(
            get("/api/ai/conversations/" + foreign.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Conversation not found"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void getConversation_forUnknownId_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/ai/conversations/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Conversation not found"));
  }

  @Test
  void readEndpoints_withoutAuthentication_return401() throws Exception {
    mockMvc.perform(get("/api/ai/conversations")).andExpect(status().isUnauthorized());

    AiConversation conversation = saveConversation(user, "mine", Instant.now());
    mockMvc
        .perform(get("/api/ai/conversations/" + conversation.getId()))
        .andExpect(status().isUnauthorized());
  }
}
