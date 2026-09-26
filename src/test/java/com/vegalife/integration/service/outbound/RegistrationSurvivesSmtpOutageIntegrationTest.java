package com.vegalife.integration.service.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.token.OtpCodeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.scheduled.OutboundMessageDrainer;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * AC1 and AC2: registration still returns 201 while SMTP refuses connections, and the failed
 * delivery attempt is rescheduled on the queue with the first increasing retry delay.
 */
@Testcontainers
@SpringBootTest(properties = "app.scheduling.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RegistrationSurvivesSmtpOutageIntegrationTest {

  private static final int DEAD_SMTP_PORT = findClosedSmtpPort();

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("vegalife_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false);

  @DynamicPropertySource
  static void outageTestProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    registry.add("spring.mail.host", () -> "127.0.0.1");
    registry.add("spring.mail.port", () -> DEAD_SMTP_PORT);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private OutboundMessageDrainer drainer;
  @Autowired private OutboundMessageRepository outboundMessageRepository;
  @Autowired private OtpCodeRepository otpCodeRepository;
  @Autowired private UserRepository userRepository;

  private String uniqueSuffix;
  private String email;

  @BeforeEach
  void setUp() {
    outboundMessageRepository.deleteAll();
    otpCodeRepository.deleteAll();
    userRepository.deleteAll();
    uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    email = "outage-" + uniqueSuffix + "@test.com";
  }

  @Test
  void register_returns201_andEnqueuesRowWhileSmtpIsDown() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest())))
        .andExpect(status().isCreated());

    List<OutboundMessage> rows = outboundMessageRepository.findAll();
    assertThat(rows).hasSize(1);
    OutboundMessage row = rows.get(0);
    assertThat(row.getChannel()).isEqualTo(OutboundChannel.EMAIL);
    assertThat(row.getStatus()).isEqualTo(OutboundStatus.PENDING);
    assertThat(row.getAttempts()).isZero();
    assertThat(row.getRecipient()).isEqualTo(email);
    assertThat(row.getPayload()).isNotBlank();
    assertThat(row.getNextAttemptAt()).isNotNull();
    assertThat(row.getExpiresAt()).isNotNull();
  }

  @Test
  void failedFirstDelivery_schedulesFirstRetryDelay_andKeepsPayload() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest())))
        .andExpect(status().isCreated());

    String payloadBefore = singleRow().getPayload();
    Instant beforeDrain = Instant.now();

    drainer.drainOnce();

    OutboundMessage retried = singleRow();
    assertThat(retried.getStatus()).isEqualTo(OutboundStatus.PENDING);
    assertThat(retried.getAttempts()).isEqualTo(1);
    assertThat(retried.getPayload()).isEqualTo(payloadBefore);
    assertThat(retried.getLockedAt()).isNull();
    assertThat(retried.getLockedBy()).isNull();
    assertThat(retried.getNextAttemptAt())
        .isBetween(beforeDrain.plusSeconds(9), beforeDrain.plusSeconds(15));

    drainer.drainOnce();
    assertThat(singleRow().getAttempts()).isEqualTo(1);
  }

  private static int findClosedSmtpPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to allocate a closed SMTP port", e);
    }
  }

  private RegisterRequest registerRequest() {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("outageuser-" + uniqueSuffix);
    request.setEmail(email);
    request.setPassword("password123");
    request.setConfirmPassword("password123");
    return request;
  }

  private OutboundMessage singleRow() {
    List<OutboundMessage> rows = outboundMessageRepository.findAll();
    assertThat(rows).hasSize(1);
    return rows.get(0);
  }
}
