package com.vegalife.integration.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.infrastructure.google.GoogleIdTokenVerifier;
import com.vegalife.infrastructure.google.GoogleTokenExchanger;
import com.vegalife.infrastructure.google.GoogleUserInfo;
import com.vegalife.model.user.AuthProvider;
import com.vegalife.model.user.User;
import com.vegalife.repository.token.OtpCodeRepository;
import com.vegalife.repository.token.RefreshTokenRepository;
import com.vegalife.repository.user.UserIdentityRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.email.EmailService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/**
 * Issue #31's acceptance criteria against a real PostgreSQL: Google sign-in creates an account,
 * reuses a linked one, and auto-links into an existing verified account so both sign-in methods
 * return the same {@code userId}.
 *
 * <p>The Google boundary is mocked at the two collaborators that talk to Google, so the resolution
 * tree, the constraints in {@code V44} and the token issuance all run for real.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GoogleAuthControllerIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg15")
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
  @Autowired private UserIdentityRepository userIdentityRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private OtpCodeRepository otpCodeRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private GoogleTokenExchanger googleTokenExchanger;
  @MockBean private GoogleIdTokenVerifier googleIdTokenVerifier;
  @MockBean private EmailService emailService;

  @BeforeEach
  void setUp() {
    userIdentityRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    otpCodeRepository.deleteAll();
    userRepository.deleteAll();
    doNothing().when(emailService).sendVerificationOtp(anyString(), anyString(), anyString());
    doNothing().when(emailService).sendPasswordResetOtp(anyString(), anyString(), anyString());
  }

  private User createActivatedPasswordUser(String username, String email) {
    return userRepository.save(
        User.builder()
            .username(username)
            .email(email)
            .passwordHash(passwordEncoder.encode("password123"))
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build());
  }

  private void stubGoogleIdentity(String subject, String email) {
    when(googleTokenExchanger.exchangeForIdToken("google-code")).thenReturn("id-token");
    when(googleIdTokenVerifier.verify("id-token"))
        .thenReturn(new GoogleUserInfo(subject, email, true, "Test"));
  }

  private String googleLoginBody() {
    return "{\"code\":\"google-code\"}";
  }

  @Test
  void googleLogin_newAccount_createsVerifiedActivatedUserAndReturnsTokens() throws Exception {
    stubGoogleIdentity("sub-new", "newcomer@gmail.com");

    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(googleLoginBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.username").value("newcomer"));

    List<User> users = userRepository.findAll();
    assertThat(users).hasSize(1);
    User created = users.get(0);
    assertThat(created.getEmail()).isEqualTo("newcomer@gmail.com");
    assertThat(created.getEmailVerified()).isTrue();
    assertThat(created.getStatus()).isEqualTo(User.Status.activated);
    assertThat(created.getPasswordHash()).isNull();
    // No email-verification OTP is issued: Google already proved the address.
    verify(emailService, never()).sendVerificationOtp(anyString(), anyString(), anyString());

    assertThat(
            userIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "sub-new"))
        .isPresent();
  }

  @Test
  void googleLogin_secondSignIn_sameAccountNoDuplicate() throws Exception {
    stubGoogleIdentity("sub-repeat", "repeat@gmail.com");

    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(googleLoginBody()))
        .andExpect(status().isOk());

    String firstUserId = userRepository.findAll().get(0).getId().toString();

    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(googleLoginBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(firstUserId));

    assertThat(userRepository.findAll()).hasSize(1);
    assertThat(userIdentityRepository.findAll()).hasSize(1);
  }

  @Test
  void googleLogin_verifiedExistingAccount_autoLinksAndBothLoginsShareUserId() throws Exception {
    User existing = createActivatedPasswordUser("existing", "existing@gmail.com");
    stubGoogleIdentity("sub-link", "existing@gmail.com");

    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(googleLoginBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(existing.getId().toString()));

    // The acceptance criterion: password login now returns the very same account.
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"existing@gmail.com\",\"password\":\"password123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(existing.getId().toString()));

    assertThat(userRepository.findAll()).hasSize(1);
    assertThat(userIdentityRepository.findAll()).hasSize(1);
  }

  @Test
  void googleLogin_unverifiedExistingAccount_refusesAndCreatesNoLink() throws Exception {
    userRepository.save(
        User.builder()
            .username("pending")
            .email("pending@gmail.com")
            .passwordHash(passwordEncoder.encode("password123"))
            .role(User.Role.USER)
            .status(User.Status.created)
            .emailVerified(false)
            .build());
    stubGoogleIdentity("sub-pending", "pending@gmail.com");

    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(googleLoginBody()))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value("Email not verified. Please verify your email before logging in."));

    assertThat(userIdentityRepository.findAll()).isEmpty();
    assertThat(userRepository.findAll()).hasSize(1);
  }

  @Test
  void passwordLogin_googleOnlyAccount_getsGenericRejectionNot500() throws Exception {
    stubGoogleIdentity("sub-only", "googleonly@gmail.com");
    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(googleLoginBody()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"googleonly@gmail.com\",\"password\":\"anything123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid email/username or password"));
  }

  @Test
  void googleLogin_blankCode_returnsValidationError() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
