package com.vegalife.integration.service.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.request.auth.VerifyEmailRequest;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.model.user.User;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.token.OtpCodeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.scheduled.OutboundMessageDrainer;
import com.vegalife.service.auth.AuthService;
import com.vegalife.service.outbound.OutboundEmailPayload;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * AC4 end-to-end: a queued verification email reaches a live SMTP inbox through the drainer, and
 * the delivered OTP then verifies the account. Scheduling stays off so the test drives each drain
 * explicitly.
 */
@Testcontainers
@SpringBootTest(properties = "app.scheduling.enabled=false")
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboundMessageDeliveryIntegrationTest {

  private static final GreenMail GREEN_MAIL = startGreenMail();

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("vegalife_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false);

  @DynamicPropertySource
  static void deliveryTestProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    registry.add("spring.mail.host", () -> "127.0.0.1");
    registry.add("spring.mail.port", () -> GREEN_MAIL.getSmtp().getPort());
  }

  @AfterAll
  static void stopGreenMail() {
    GREEN_MAIL.stop();
  }

  @Autowired private AuthService authService;
  @Autowired private OutboundMessageDrainer drainer;
  @Autowired private OutboundMessageRepository outboundMessageRepository;
  @Autowired private OtpCodeRepository otpCodeRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  private String uniqueSuffix;
  private String email;
  private String username;

  @BeforeEach
  void setUp() throws Exception {
    // purge, never reset(): reset() restarts the servers on a fresh dynamic port while
    // spring.mail.port keeps the port snapshotted at context startup, breaking delivery
    GREEN_MAIL.purgeEmailFromAllMailboxes();
    outboundMessageRepository.deleteAll();
    otpCodeRepository.deleteAll();
    userRepository.deleteAll();
    uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    username = "deliveryuser-" + uniqueSuffix;
    email = "delivery-" + uniqueSuffix + "@test.com";
  }

  @Test
  void deliveredOtpReachesLiveSmtpInbox_andRowSettlesCompleted() throws Exception {
    authService.register(registerRequest());
    String otp = payloadOf(singleRow()).otp();

    drainer.drainOnce();

    OutboundMessage settled = singleRow();
    assertThat(settled.getStatus()).isEqualTo(OutboundStatus.COMPLETED);
    assertThat(settled.getPayload()).isNull();
    assertThat(settled.getCompletedAt()).isNotNull();
    assertThat(settled.getLockedAt()).isNull();
    assertThat(settled.getLockedBy()).isNull();

    assertThat(GREEN_MAIL.waitForIncomingEmail(5_000, 1)).isTrue();
    MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
    assertThat(received).hasSize(1);
    MimeMessage mail = received[0];
    assertThat(((InternetAddress) mail.getAllRecipients()[0]).getAddress()).isEqualTo(email);
    assertThat(mail.getSubject()).contains("Verify your email");
    assertThat(collectText(mail)).contains(otp);

    drainer.drainOnce();
    assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
  }

  @Test
  void deliveredOtpFromQueue_verifiesTheAccount() throws Exception {
    authService.register(registerRequest());
    String otp = payloadOf(singleRow()).otp();

    drainer.drainOnce();
    assertThat(GREEN_MAIL.waitForIncomingEmail(5_000, 1)).isTrue();

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.getEmailVerified()).isFalse();

    VerifyEmailRequest request = new VerifyEmailRequest();
    request.setEmail(email);
    request.setOtp(otp);
    authService.verifyEmail(request);

    User verified = userRepository.findById(user.getId()).orElseThrow();
    assertThat(verified.getEmailVerified()).isTrue();
    assertThat(verified.getStatus()).isEqualTo(User.Status.activated);
  }

  private static GreenMail startGreenMail() {
    GreenMail mail = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
    mail.start();
    return mail;
  }

  private static String collectText(Part part) throws Exception {
    Object content = part.getContent();
    if (content instanceof String text) {
      return text;
    }
    if (content instanceof Multipart multipart) {
      StringBuilder text = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        text.append(collectText(multipart.getBodyPart(i)));
      }
      return text.toString();
    }
    return "";
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

  private OutboundEmailPayload payloadOf(OutboundMessage row) throws Exception {
    return objectMapper.readValue(row.getPayload(), OutboundEmailPayload.class);
  }
}
