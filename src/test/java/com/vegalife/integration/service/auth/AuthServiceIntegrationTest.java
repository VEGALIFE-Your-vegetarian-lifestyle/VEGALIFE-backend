package com.vegalife.integration.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.request.auth.VerifyEmailRequest;
import com.vegalife.dto.response.auth.RegisterResponse;
import com.vegalife.model.user.User;
import com.vegalife.model.user.User.Role;
import com.vegalife.model.user.User.Status;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.auth.AuthService;
import com.vegalife.service.email.EmailService;
import com.vegalife.shared.exception.DuplicateResourceException;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthServiceIntegrationTest {

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

  @Autowired private AuthService authService;

  @Autowired private UserRepository userRepository;

  @MockBean private EmailService emailService;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    doNothing().when(emailService).sendVerificationOtp(anyString(), anyString(), anyString());
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

  private RegisterRequest registerRequest(String username, String email) {
    RegisterRequest request = new RegisterRequest();
    request.setUsername(username);
    request.setEmail(email);
    request.setPassword("password123");
    request.setConfirmPassword("password123");
    return request;
  }

  @Test
  void register_savesUserToDatabase() {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("dbuser");
    request.setEmail("dbuser@test.com");
    request.setPassword("password123");
    request.setConfirmPassword("password123");

    RegisterResponse response = authService.register(request);

    assertThat(response).isNotNull();
    assertThat(response.getUsername()).isEqualTo("dbuser");
    assertThat(response.getEmail()).isEqualTo("dbuser@test.com");

    User savedUser = userRepository.findByEmail("dbuser@test.com").orElseThrow();
    assertThat(savedUser.getUsername()).isEqualTo("dbuser");
    assertThat(savedUser.getEmail()).isEqualTo("dbuser@test.com");
    assertThat(savedUser.getPasswordHash()).isNotNull();
    assertThat(savedUser.getEmailVerified()).isFalse();
    assertThat(savedUser.getStatus()).isEqualTo(Status.created);
    assertThat(savedUser.getRole()).isEqualTo(Role.USER);
  }

  @Test
  void register_duplicateEmail_throwsException() {
    RegisterRequest request1 = new RegisterRequest();
    request1.setUsername("user1");
    request1.setEmail("same@test.com");
    request1.setPassword("password123");
    request1.setConfirmPassword("password123");
    authService.register(request1);

    RegisterRequest request2 = new RegisterRequest();
    request2.setUsername("user2");
    request2.setEmail("same@test.com");
    request2.setPassword("password123");
    request2.setConfirmPassword("password123");

    assertThatThrownBy(() -> authService.register(request2))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Email already registered");
  }

  @Test
  void register_duplicateUsername_throwsException() {
    RegisterRequest request1 = new RegisterRequest();
    request1.setUsername("sameuser");
    request1.setEmail("user1@test.com");
    request1.setPassword("password123");
    request1.setConfirmPassword("password123");
    authService.register(request1);

    RegisterRequest request2 = new RegisterRequest();
    request2.setUsername("sameuser");
    request2.setEmail("user2@test.com");
    request2.setPassword("password123");
    request2.setConfirmPassword("password123");

    assertThatThrownBy(() -> authService.register(request2))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Username already taken");
  }

  @Test
  void verifyEmail_activatesUser() {
    authService.register(registerRequest("verifyuser", "verify@test.com"));
    String otp = captureVerificationOtp("verify@test.com");
    User user = userRepository.findByEmail("verify@test.com").orElseThrow();

    RegisterResponse verifyResponse =
        authService.verifyEmail(verifyRequest("verify@test.com", otp));

    assertThat(verifyResponse).isNotNull();

    User verifiedUser = userRepository.findById(user.getId()).orElseThrow();
    assertThat(verifiedUser.getEmailVerified()).isTrue();
    assertThat(verifiedUser.getStatus()).isEqualTo(Status.activated);
  }

  @Test
  void verifyEmail_alreadyVerified_returnsSuccess() {
    authService.register(registerRequest("verifyuser2", "verify2@test.com"));
    String otp = captureVerificationOtp("verify2@test.com");
    User user = userRepository.findByEmail("verify2@test.com").orElseThrow();

    authService.verifyEmail(verifyRequest("verify2@test.com", otp));

    RegisterResponse verifyResponse =
        authService.verifyEmail(verifyRequest("verify2@test.com", otp));
    assertThat(verifyResponse).isNotNull();

    User verifiedUser = userRepository.findById(user.getId()).orElseThrow();
    assertThat(verifiedUser.getEmailVerified()).isTrue();
    assertThat(verifiedUser.getStatus()).isEqualTo(Status.activated);
  }

  @Test
  void verifyEmail_wrongOtp_throwsInvalidTokenException() {
    authService.register(registerRequest("wrongotpuser", "wrongotp@test.com"));
    captureVerificationOtp("wrongotp@test.com");

    assertThatThrownBy(() -> authService.verifyEmail(verifyRequest("wrongotp@test.com", "000000")))
        .isInstanceOf(com.vegalife.shared.exception.InvalidTokenException.class)
        .hasMessage("Invalid or already used verification code");
  }
}
