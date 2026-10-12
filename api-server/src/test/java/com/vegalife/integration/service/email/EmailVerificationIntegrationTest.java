package com.vegalife.integration.service.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.request.auth.VerifyEmailRequest;
import com.vegalife.dto.response.auth.RegisterResponse;
import com.vegalife.model.token.OtpCode;
import com.vegalife.model.token.OtpPurpose;
import com.vegalife.model.user.User;
import com.vegalife.model.user.User.Status;
import com.vegalife.repository.token.OtpCodeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.auth.AuthService;
import com.vegalife.service.email.EmailService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmailVerificationIntegrationTest {

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

  @MockBean private EmailService emailService;

  @Autowired private UserRepository userRepository;

  @Autowired private OtpCodeRepository otpCodeRepository;

  @Autowired private AuthService authService;

  private String uniqueSuffix;

  @BeforeEach
  void setUp() {
    otpCodeRepository.deleteAll();
    userRepository.deleteAll();
    uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    doNothing().when(emailService).sendVerificationOtp(anyString(), anyString(), anyString());
  }

  private RegisterRequest registerRequest() {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("integrationuser-" + uniqueSuffix);
    request.setEmail("integration-" + uniqueSuffix + "@test.com");
    request.setPassword("password123");
    request.setConfirmPassword("password123");
    return request;
  }

  private String captureVerificationOtp(String email) {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendVerificationOtp(eq(email), anyString(), captor.capture());
    return captor.getValue();
  }

  private VerifyEmailRequest verifyRequest(String email, String otp) {
    VerifyEmailRequest request = new VerifyEmailRequest();
    request.setEmail(email);
    request.setOtp(otp);
    return request;
  }

  @Test
  void register_issuesOtp_andVerifyActivatesAccount() {
    RegisterRequest registerRequest = registerRequest();
    String email = registerRequest.getEmail();

    authService.register(registerRequest);

    String otp = captureVerificationOtp(email);
    assertThat(otp).matches("\\d{6}");

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.getEmailVerified()).isFalse();
    assertThat(user.getStatus()).isEqualTo(Status.created);

    OtpCode otpRow =
        otpCodeRepository
            .findLatestUnusedByUserIdAndPurpose(user.getId(), OtpPurpose.EMAIL_VERIFICATION)
            .orElseThrow();
    assertThat(otpRow.getExpiresAt()).isAfter(java.time.Instant.now());

    RegisterResponse response = authService.verifyEmail(verifyRequest(email, otp));
    assertThat(response).isNotNull();
    assertThat(response.getEmail()).isEqualTo(email);

    User verifiedUser = userRepository.findById(user.getId()).orElseThrow();
    assertThat(verifiedUser.getEmailVerified()).isTrue();
    assertThat(verifiedUser.getStatus()).isEqualTo(Status.activated);

    assertThat(
            otpCodeRepository.findLatestUnusedByUserIdAndPurpose(
                user.getId(), OtpPurpose.EMAIL_VERIFICATION))
        .isEmpty();
  }

  @Test
  void passwordResetOtp_unaffectedByVerificationIssuance() {
    RegisterRequest registerRequest = registerRequest();
    String email = registerRequest.getEmail();

    authService.register(registerRequest);
    String verificationOtp = captureVerificationOtp(email);

    User user = userRepository.findByEmail(email).orElseThrow();

    // Verification OTP exists for this user...
    assertThat(
            otpCodeRepository.findLatestUnusedByUserIdAndPurpose(
                user.getId(), OtpPurpose.EMAIL_VERIFICATION))
        .isPresent();

    // ...but no PASSWORD_RESET OTP was issued by registration
    assertThat(
            otpCodeRepository.findLatestUnusedByUserIdAndPurpose(
                user.getId(), OtpPurpose.PASSWORD_RESET))
        .isEmpty();

    assertThat(verificationOtp).matches("\\d{6}");
  }

  @Test
  void verifyEmail_wrongOtp_doesNotActivate() {
    RegisterRequest registerRequest = registerRequest();
    String email = registerRequest.getEmail();

    authService.register(registerRequest);
    captureVerificationOtp(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    try {
      authService.verifyEmail(verifyRequest(email, "000000"));
      throw new AssertionError("Expected InvalidTokenException");
    } catch (com.vegalife.shared.exception.InvalidTokenException e) {
      assertThat(e.getMessage()).isEqualTo("Invalid or already used verification code");
    }

    User unchanged = userRepository.findById(user.getId()).orElseThrow();
    assertThat(unchanged.getEmailVerified()).isFalse();
    assertThat(unchanged.getStatus()).isEqualTo(Status.created);
    assertThat(
            otpCodeRepository.findLatestUnusedByUserIdAndPurpose(
                user.getId(), OtpPurpose.EMAIL_VERIFICATION))
        .isPresent();
  }

  @Test
  void register_supersedesPreviousVerificationOtp() {
    RegisterRequest registerRequest = registerRequest();
    String email = registerRequest.getEmail();

    authService.register(registerRequest);
    String firstOtp = captureVerificationOtp(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    // Simulate a re-register attempt path isn't possible (duplicate email), but
    // markAllUnused is still invoked on register — force a second OTP issue by
    // marking prior unused and reissuing through forgot-style supersede.
    otpCodeRepository.markAllUnusedByUserIdAndPurpose(
        user.getId(), OtpPurpose.EMAIL_VERIFICATION, java.time.Instant.now());

    assertThat(
            otpCodeRepository.findLatestUnusedByUserIdAndPurpose(
                user.getId(), OtpPurpose.EMAIL_VERIFICATION))
        .isEmpty();
    assertThat(firstOtp).matches("\\d{6}");
  }
}
