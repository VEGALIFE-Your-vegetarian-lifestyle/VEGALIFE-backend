package com.vegalife.integration.service.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.request.auth.ForgotPasswordRequest;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.request.auth.ResendVerificationOtpRequest;
import com.vegalife.infrastructure.email.SmtpEmailServiceImpl;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.token.OtpCodeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.auth.AuthService;
import com.vegalife.service.outbound.OutboundEmailPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 3 seam test: auth flows enqueue OUTBOUND_MESSAGE rows via {@code OutboxEmailService}
 * instead of talking to SMTP. Scheduling is disabled so the drainer cannot consume rows mid-test.
 */
@Testcontainers
@SpringBootTest(properties = "app.scheduling.enabled=false")
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OutboxEmailServiceIntegrationTest {

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

  @MockBean private SmtpEmailServiceImpl smtpEmailService;

  @Autowired private AuthService authService;
  @Autowired private OutboundMessageRepository outboundMessageRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private OtpCodeRepository otpCodeRepository;
  @Autowired private ObjectMapper objectMapper;

  private String uniqueSuffix;
  private String email;
  private String username;

  @BeforeEach
  void setUp() {
    outboundMessageRepository.deleteAll();
    otpCodeRepository.deleteAll();
    userRepository.deleteAll();
    uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    username = "outboxuser-" + uniqueSuffix;
    email = "outbox-" + uniqueSuffix + "@test.com";
  }

  private RegisterRequest registerRequest() {
    RegisterRequest request = new RegisterRequest();
    request.setUsername(username);
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

  private void assertRowEnvelope(OutboundMessage row) {
    assertThat(row.getChannel()).isEqualTo(OutboundChannel.EMAIL);
    assertThat(row.getStatus()).isEqualTo(OutboundStatus.PENDING);
    assertThat(row.getAttempts()).isZero();
    assertThat(row.getRecipient()).isEqualTo(email);
    assertThat(row.getPayload()).isNotBlank();
    assertThat(row.getNextAttemptAt()).isNotNull();
    assertThat(row.getExpiresAt()).isNotNull();
    // business expiry = OTP validity (10 minutes by default), measured from enqueue time
    assertThat(row.getExpiresAt())
        .isBetween(
            Instant.now().plus(Duration.ofMinutes(9)), Instant.now().plus(Duration.ofMinutes(11)));
  }

  @Test
  void register_enqueuesPendingVerificationRow_andNeverTouchesSmtp() throws Exception {
    authService.register(registerRequest());

    OutboundMessage row = singleRow();
    assertRowEnvelope(row);

    OutboundEmailPayload payload =
        objectMapper.readValue(row.getPayload(), OutboundEmailPayload.class);
    assertThat(payload.type()).isEqualTo(OutboundEmailPayload.Type.EMAIL_VERIFICATION);
    assertThat(payload.username()).isEqualTo(username);
    assertThat(payload.otp()).matches("\\d{6}");
    assertThat(payload.expiryMinutes()).isEqualTo(10);

    verifyNoInteractions(smtpEmailService);
  }

  @Test
  void forgotPassword_enqueuesPendingResetRow_andNeverTouchesSmtp() throws Exception {
    authService.register(registerRequest());
    outboundMessageRepository.deleteAll();

    ForgotPasswordRequest request = new ForgotPasswordRequest();
    request.setEmail(email);
    authService.forgotPassword(request);

    OutboundMessage row = singleRow();
    assertRowEnvelope(row);

    OutboundEmailPayload payload =
        objectMapper.readValue(row.getPayload(), OutboundEmailPayload.class);
    assertThat(payload.type()).isEqualTo(OutboundEmailPayload.Type.PASSWORD_RESET);
    assertThat(payload.username()).isEqualTo(username);
    assertThat(payload.otp()).matches("\\d{6}");
    assertThat(payload.expiryMinutes()).isEqualTo(10);

    verifyNoInteractions(smtpEmailService);
  }

  @Test
  void resendVerificationOtp_enqueuesPendingRow_andNeverTouchesSmtp() throws Exception {
    authService.register(registerRequest());
    outboundMessageRepository.deleteAll();

    ResendVerificationOtpRequest request = new ResendVerificationOtpRequest();
    request.setEmail(email);
    authService.resendVerificationOtp(request);

    OutboundMessage row = singleRow();
    assertRowEnvelope(row);

    OutboundEmailPayload payload =
        objectMapper.readValue(row.getPayload(), OutboundEmailPayload.class);
    assertThat(payload.type()).isEqualTo(OutboundEmailPayload.Type.EMAIL_VERIFICATION);

    verifyNoInteractions(smtpEmailService);
  }
}
