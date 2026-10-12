package com.vegalife.integration.controller.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vegalife.model.user.User;
import com.vegalife.repository.ai.AiConversationRepository;
import com.vegalife.repository.ai.AiMessageRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Env-gated round trip against the real OpenAI-compatible chat provider (BR-AI-005): the full
 * endpoint must reach the configured {@code AI_CHAT_*} endpoint and persist a reply. Skips when
 * {@code AI_CHAT_API_KEY} / {@code HF_TOKEN} are absent — such a run never proves the provider
 * exchange, only that the rest of the suite is green. Mirrors the VNPay sandbox smoke test.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiChatProviderSmokeIntegrationTest {

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
  @Autowired private AiUsageRepository usageRepository;
  @Autowired private JwtTokenService jwtTokenService;

  private User user;
  private String accessToken;

  @BeforeEach
  void requireProviderCredentials() {
    Assumptions.assumeTrue(
        present(System.getenv("AI_CHAT_API_KEY")) || present(System.getenv("HF_TOKEN")),
        "AI_CHAT_API_KEY / HF_TOKEN not set - skipping real chat provider round trip");

    user =
        userRepository.save(
            User.builder()
                .username("smoke" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    accessToken = jwtTokenService.generateAccessToken(user);
  }

  @Test
  void sendMessage_reachesRealProviderAndPersistsReply() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/ai/messages")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"Reply with the single word: ok\"}"))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    String reply = JsonPath.read(body, "$.data.reply");
    assertThat(reply).isNotBlank();

    String conversationId = JsonPath.read(body, "$.data.conversationId");
    assertThat(
            messageRepository.findTop20ByConversationIdOrderByCreatedAtDescIdDesc(
                UUID.fromString(conversationId)))
        .hasSize(2);
    assertThat(
            conversationRepository.findByIdAndUserId(UUID.fromString(conversationId), user.getId()))
        .isPresent();
    assertThat(usageRepository.findAll()).isNotEmpty();
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
