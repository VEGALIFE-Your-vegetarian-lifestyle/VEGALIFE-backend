package com.vegalife.integration.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.model.token.OtpCode;
import com.vegalife.model.token.OtpPurpose;
import com.vegalife.model.token.RefreshToken;
import com.vegalife.model.user.User;
import com.vegalife.model.user.User.Status;
import com.vegalife.repository.token.OtpCodeRepository;
import com.vegalife.repository.token.RefreshTokenRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.email.EmailService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerIntegrationTest {

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

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Autowired private OtpCodeRepository otpCodeRepository;

  @MockBean private EmailService emailService;

  @BeforeEach
  void setUp() {
    otpCodeRepository.deleteAll();
    userRepository.deleteAll();
    doNothing().when(emailService).sendVerificationOtp(anyString(), anyString(), anyString());
    doNothing().when(emailService).sendPasswordResetOtp(anyString(), anyString(), anyString());
  }

  private User createActivatedUser(String username, String email) {
    return userRepository.save(
        User.builder()
            .username(username)
            .email(email)
            .passwordHash(passwordEncoder.encode("oldPassword1"))
            .role(User.Role.USER)
            .status(Status.activated)
            .emailVerified(true)
            .build());
  }

  private String captureOtp(String email) {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendPasswordResetOtp(eq(email), anyString(), captor.capture());
    return captor.getValue();
  }

  private String captureVerificationOtp(String email) {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendVerificationOtp(eq(email), anyString(), captor.capture());
    return captor.getValue();
  }

  private void registerUser(String username, String email) throws Exception {
    RegisterRequest request = new RegisterRequest();
    request.setUsername(username);
    request.setEmail(email);
    request.setPassword("password123");
    request.setConfirmPassword("password123");

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  private String verifyEmailBody(String email, String otp) {
    return "{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}";
  }

  @Test
  void register_thenVerifyEmail_thenLogin_fullFlow() throws Exception {
    registerUser("integrationuser", "integration@test.com");

    User user = userRepository.findByEmail("integration@test.com").orElseThrow();
    assertThat(user.getEmailVerified()).isFalse();
    assertThat(user.getStatus()).isEqualTo(Status.created);

    String otp = captureVerificationOtp("integration@test.com");
    assertThat(otp).matches("\\d{6}");

    mockMvc
        .perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyEmailBody("integration@test.com", otp)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Email verified successfully"));

    User verifiedUser = userRepository.findById(user.getId()).orElseThrow();
    assertThat(verifiedUser.getEmailVerified()).isTrue();
    assertThat(verifiedUser.getStatus()).isEqualTo(Status.activated);

    OtpCode consumed =
        otpCodeRepository
            .findLatestUnusedByUserIdAndPurpose(user.getId(), OtpPurpose.EMAIL_VERIFICATION)
            .orElse(null);
    assertThat(consumed).isNull();

    String loginBody = "{\"identifier\":\"integration@test.com\",\"password\":\"password123\"}";
    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Login successful"));
  }

  @Test
  void register_duplicateEmail_returnsConflict() throws Exception {
    registerUser("user1", "duplicate@test.com");

    RegisterRequest secondRequest = new RegisterRequest();
    secondRequest.setUsername("user2");
    secondRequest.setEmail("duplicate@test.com");
    secondRequest.setPassword("password123");
    secondRequest.setConfirmPassword("password123");

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondRequest)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Email already registered"));
  }

  @Test
  void register_duplicateUsername_returnsConflict() throws Exception {
    registerUser("sameuser", "user1@test.com");

    RegisterRequest secondRequest = new RegisterRequest();
    secondRequest.setUsername("sameuser");
    secondRequest.setEmail("user2@test.com");
    secondRequest.setPassword("password123");
    secondRequest.setConfirmPassword("password123");

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondRequest)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Username already taken"));
  }

  @Test
  void verifyEmail_unknownEmail_returns400_genericMessage() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyEmailBody("nobody@test.com", "123456")))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Invalid or already used verification code"));
  }

  @Test
  void verifyEmail_wrongOtp_returns400() throws Exception {
    registerUser("wrongverify", "wrongverify@test.com");
    captureVerificationOtp("wrongverify@test.com");

    mockMvc
        .perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyEmailBody("wrongverify@test.com", "000000")))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Invalid or already used verification code"));

    User user = userRepository.findByEmail("wrongverify@test.com").orElseThrow();
    assertThat(user.getEmailVerified()).isFalse();
  }

  @Test
  void verifyEmail_expiredOtp_returns400() throws Exception {
    registerUser("expiredverify", "expiredverify@test.com");
    User user = userRepository.findByEmail("expiredverify@test.com").orElseThrow();
    String otp = captureVerificationOtp("expiredverify@test.com");

    OtpCode row =
        otpCodeRepository
            .findLatestUnusedByUserIdAndPurpose(user.getId(), OtpPurpose.EMAIL_VERIFICATION)
            .orElseThrow();
    row.setExpiresAt(Instant.now().minusSeconds(1));
    otpCodeRepository.save(row);

    mockMvc
        .perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyEmailBody("expiredverify@test.com", otp)))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(
            jsonPath("$.message")
                .value("Verification code has expired. Please request a new one."));
  }

  @Test
  void verifyEmail_reusedOtp_returns400() throws Exception {
    registerUser("reuseverify", "reuseverify@test.com");
    String otp = captureVerificationOtp("reuseverify@test.com");

    mockMvc
        .perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyEmailBody("reuseverify@test.com", otp)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyEmailBody("reuseverify@test.com", otp)))
        .andExpect(status().isOk());
  }

  @Test
  void resendEmail_unverifiedUser_supersedesPreviousOtp() throws Exception {
    registerUser("resenduser", "resend@test.com");
    String firstOtp = captureVerificationOtp("resend@test.com");

    mockMvc
        .perform(
            post("/api/auth/resend-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"resend@test.com\"}"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(
            jsonPath("$.message")
                .value("If an account with that email exists, a verification code has been sent"));

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(emailService, times(2))
        .sendVerificationOtp(eq("resend@test.com"), anyString(), captor.capture());
    String secondOtp = captor.getAllValues().get(1);
    assertThat(secondOtp).matches("\\d{6}");

    mockMvc
        .perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyEmailBody("resend@test.com", firstOtp)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid or already used verification code"));

    mockMvc
        .perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyEmailBody("resend@test.com", secondOtp)))
        .andExpect(status().isOk());

    User user = userRepository.findByEmail("resend@test.com").orElseThrow();
    assertThat(user.getEmailVerified()).isTrue();
    assertThat(user.getStatus()).isEqualTo(Status.activated);
  }

  @Test
  void resendEmail_unknownEmail_returns200_genericMessage() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/resend-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ghost@test.com\"}"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(
            jsonPath("$.message")
                .value("If an account with that email exists, a verification code has been sent"));

    verify(emailService, never())
        .sendVerificationOtp(eq("ghost@test.com"), anyString(), anyString());
  }

  @Test
  void resendEmail_alreadyVerified_returns200_withoutSending() throws Exception {
    createActivatedUser("verifieduser", "verified@test.com");

    mockMvc
        .perform(
            post("/api/auth/resend-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"verified@test.com\"}"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(
            jsonPath("$.message")
                .value("If an account with that email exists, a verification code has been sent"));

    verify(emailService, never())
        .sendVerificationOtp(eq("verified@test.com"), anyString(), anyString());
  }

  @Test
  void login_suspendedAccount_returnsBadRequestWithSuspendedMessage() throws Exception {
    User suspendedUser =
        userRepository.save(
            User.builder()
                .username("suspendedlogin")
                .email("suspendedlogin@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(User.Role.USER)
                .status(User.Status.suspended)
                .emailVerified(true)
                .build());

    String body = "{\"identifier\":\"suspendedlogin@test.com\",\"password\":\"password123\"}";

    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Account is suspended"));

    assertThat(userRepository.findById(suspendedUser.getId()).orElseThrow().getStatus())
        .isEqualTo(User.Status.suspended);
  }

  @Test
  void login_deactivatedAccount_returnsBadRequestWithNotActiveMessage() throws Exception {
    userRepository.save(
        User.builder()
            .username("deactlogin")
            .email("deactlogin@test.com")
            .passwordHash(passwordEncoder.encode("password123"))
            .role(User.Role.USER)
            .status(User.Status.deactivated)
            .emailVerified(true)
            .build());

    String body = "{\"identifier\":\"deactlogin@test.com\",\"password\":\"password123\"}";

    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Account is not active"));
  }

  @Test
  void refreshToken_suspendedAccount_returnsBadRequest() throws Exception {
    User suspendedUser =
        userRepository.save(
            User.builder()
                .username("refreshsuspended")
                .email("refreshsuspended@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(User.Role.USER)
                .status(User.Status.suspended)
                .emailVerified(true)
                .build());
    com.vegalife.model.token.RefreshToken stored =
        com.vegalife.model.token.RefreshToken.builder()
            .user(suspendedUser)
            .tokenHash("hashed.value")
            .expiresAt(java.time.Instant.now().plusSeconds(604800))
            .revokedAt(null)
            .build();
    refreshTokenRepository.save(stored);

    String body = "{\"refreshToken\":\"any.token.value\"}";

    mockMvc
        .perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void forgotPassword_thenReset_fullFlow() throws Exception {
    User user = createActivatedUser("forgotflow", "forgotflow@test.com");
    RefreshToken activeToken =
        RefreshToken.builder()
            .user(user)
            .tokenHash("active.refresh.hash")
            .expiresAt(Instant.now().plusSeconds(604800))
            .revokedAt(null)
            .build();
    refreshTokenRepository.save(activeToken);

    String forgotBody = "{\"email\":\"forgotflow@test.com\"}";
    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotBody))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "If an account with that email exists, a password reset code has been sent"));

    String otp = captureOtp("forgotflow@test.com");
    assertThat(otp).matches("\\d{6}");

    String resetBody =
        "{\"email\":\"forgotflow@test.com\",\"otp\":\""
            + otp
            + "\",\"newPassword\":\"newPassword123\"}";
    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Password has been reset successfully"));

    User updated = userRepository.findById(user.getId()).orElseThrow();
    assertThat(passwordEncoder.matches("newPassword123", updated.getPasswordHash())).isTrue();
    assertThat(passwordEncoder.matches("oldPassword1", updated.getPasswordHash())).isFalse();

    RefreshToken revoked = refreshTokenRepository.findById(activeToken.getId()).orElseThrow();
    assertThat(revoked.getRevokedAt()).isNotNull();

    OtpCode otpRow =
        otpCodeRepository
            .findLatestUnusedByUserIdAndPurpose(user.getId(), OtpPurpose.PASSWORD_RESET)
            .orElse(null);
    assertThat(otpRow).isNull();

    String oldLogin = "{\"identifier\":\"forgotflow@test.com\",\"password\":\"oldPassword1\"}";
    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(oldLogin))
        .andExpect(status().isBadRequest());

    String newLogin = "{\"identifier\":\"forgotflow@test.com\",\"password\":\"newPassword123\"}";
    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(newLogin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Login successful"));
  }

  @Test
  void forgotPassword_unknownEmail_returnsGenericSuccess_withoutEmail() throws Exception {
    String body = "{\"email\":\"nobody@test.com\"}";

    mockMvc
        .perform(
            post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "If an account with that email exists, a password reset code has been sent"));

    verify(emailService, never()).sendPasswordResetOtp(anyString(), anyString(), anyString());
  }

  @Test
  void resetPassword_wrongOtp_returns400() throws Exception {
    createActivatedUser("wrongotp", "wrongotp@test.com");

    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"wrongotp@test.com\"}"))
        .andExpect(status().isOk());

    captureOtp("wrongotp@test.com");

    String body =
        "{\"email\":\"wrongotp@test.com\",\"otp\":\"000000\",\"newPassword\":\"newPassword123\"}";
    mockMvc
        .perform(
            post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Invalid or already used password reset code"));
  }

  @Test
  void resetPassword_unknownEmail_returns400() throws Exception {
    String body =
        "{\"email\":\"nobody@test.com\",\"otp\":\"482913\",\"newPassword\":\"newPassword123\"}";

    mockMvc
        .perform(
            post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Invalid or already used password reset code"));
  }

  @Test
  void resetPassword_expiredOtp_returns400() throws Exception {
    User user = createActivatedUser("expiredotp", "expiredotp@test.com");

    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"expiredotp@test.com\"}"))
        .andExpect(status().isOk());

    String otp = captureOtp("expiredotp@test.com");

    OtpCode row =
        otpCodeRepository
            .findLatestUnusedByUserIdAndPurpose(user.getId(), OtpPurpose.PASSWORD_RESET)
            .orElseThrow();
    row.setExpiresAt(Instant.now().minusSeconds(1));
    otpCodeRepository.save(row);

    String body =
        "{\"email\":\"expiredotp@test.com\",\"otp\":\""
            + otp
            + "\",\"newPassword\":\"newPassword123\"}";
    mockMvc
        .perform(
            post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(
            jsonPath("$.message")
                .value("Password reset code has expired. Please request a new one."));
  }

  @Test
  void forgotPassword_resend_supersedesPreviousOtp() throws Exception {
    User user = createActivatedUser("resendotp", "resendotp@test.com");

    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"resendotp@test.com\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"resendotp@test.com\"}"))
        .andExpect(status().isOk());

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(emailService, org.mockito.Mockito.times(2))
        .sendPasswordResetOtp(eq("resendotp@test.com"), anyString(), captor.capture());
    List<String> otps = captor.getAllValues();
    String firstOtp = otps.get(0);
    String secondOtp = otps.get(1);
    assertThat(firstOtp).isNotEqualTo(secondOtp);

    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"resendotp@test.com\",\"otp\":\""
                        + firstOtp
                        + "\",\"newPassword\":\"newPassword123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid or already used password reset code"));

    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"resendotp@test.com\",\"otp\":\""
                        + secondOtp
                        + "\",\"newPassword\":\"newPassword123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Password has been reset successfully"));

    assertThat(
            otpCodeRepository.findLatestUnusedByUserIdAndPurpose(
                user.getId(), OtpPurpose.PASSWORD_RESET))
        .isEmpty();
  }

  @Test
  void resetPassword_reusedOtp_returns400() throws Exception {
    User user = createActivatedUser("reusedotp", "reusedotp@test.com");

    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reusedotp@test.com\"}"))
        .andExpect(status().isOk());

    String otp = captureOtp("reusedotp@test.com");
    String body =
        "{\"email\":\"reusedotp@test.com\",\"otp\":\""
            + otp
            + "\",\"newPassword\":\"newPassword123\"}";

    mockMvc
        .perform(
            post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid or already used password reset code"));

    assertThat(userRepository.findById(user.getId())).isPresent();
  }

  @Test
  void resetPassword_invalidRequestBody_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bad\",\"otp\":\"12\",\"newPassword\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }
}
