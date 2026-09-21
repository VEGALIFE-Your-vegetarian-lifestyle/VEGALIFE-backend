package com.vegalife.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.UserMapper;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.response.auth.AuthResponse;
import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.email.EmailService;
import com.vegalife.service.token.VerificationTokenService;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ExpiredTokenException;
import com.vegalife.shared.exception.InvalidTokenException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private UserMapper userMapper;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private VerificationTokenService tokenService;

  @Mock private EmailService emailService;

  @InjectMocks private UserService userService;

  private RegisterRequest validRequest;
  private User user;
  private String encodedPassword;
  private String token;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    validRequest = new RegisterRequest();
    validRequest.setUsername("testuser");
    validRequest.setEmail("test@example.com");
    validRequest.setPassword("password123");
    validRequest.setConfirmPassword("password123");

    user =
        User.builder()
            .id(userId)
            .username("testuser")
            .email("test@example.com")
            .passwordHash("encoded")
            .role(User.Role.USER)
            .status(User.Status.created)
            .emailVerified(false)
            .build();

    encodedPassword = "encodedPassword123";
    token = "valid.jwt.token";
  }

  @Test
  void register_success() {
    when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
    when(userRepository.existsByUsername(validRequest.getUsername())).thenReturn(false);
    when(passwordEncoder.encode(validRequest.getPassword())).thenReturn(encodedPassword);
    when(userMapper.toEntity(validRequest, encodedPassword)).thenReturn(user);
    when(userRepository.save(any(User.class))).thenReturn(user);
    when(tokenService.generateToken(user)).thenReturn(token);
    when(userMapper.toAuthResponse(user, "Verification email sent. Please check your inbox."))
        .thenReturn(
            AuthResponse.builder()
                .userId(userId)
                .username("testuser")
                .email("test@example.com")
                .message("Verification email sent. Please check your inbox.")
                .build());

    AuthResponse response = userService.register(validRequest);

    assertThat(response).isNotNull();
    assertThat(response.getUserId()).isEqualTo(userId);
    assertThat(response.getUsername()).isEqualTo("testuser");
    assertThat(response.getEmail()).isEqualTo("test@example.com");
    assertThat(response.getMessage()).contains("Verification email sent");

    verify(userRepository).save(user);
    verify(tokenService).generateToken(user);
    verify(emailService).sendVerificationEmail(eq("test@example.com"), eq("testuser"), anyString());
  }

  @Test
  void register_passwordsDoNotMatch_throwsException() {
    validRequest.setConfirmPassword("different");

    assertThatThrownBy(() -> userService.register(validRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Passwords do not match");

    verify(userRepository, never()).save(any());
  }

  @Test
  void register_duplicateEmail_throwsException() {
    when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(true);

    assertThatThrownBy(() -> userService.register(validRequest))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Email already registered");

    verify(userRepository, never()).save(any());
  }

  @Test
  void register_duplicateUsername_throwsException() {
    when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
    when(userRepository.existsByUsername(validRequest.getUsername())).thenReturn(true);

    assertThatThrownBy(() -> userService.register(validRequest))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Username already taken");
  }

  @Test
  void verifyEmail_success() {
    when(tokenService.getUserIdFromToken(token)).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenReturn(user);
    when(userMapper.toAuthResponse(user, "Email verified successfully. You can now log in."))
        .thenReturn(
            AuthResponse.builder()
                .userId(userId)
                .username("testuser")
                .email("test@example.com")
                .message("Email verified successfully. You can now log in.")
                .build());

    AuthResponse response = userService.verifyEmail(token);

    assertThat(response).isNotNull();
    assertThat(response.getMessage()).contains("Email verified successfully");
    assertThat(user.getEmailVerified()).isTrue();
    assertThat(user.getStatus()).isEqualTo(User.Status.activated);
  }

  @Test
  void verifyEmail_alreadyVerified_returnsSuccess() {
    user.setEmailVerified(true);
    user.setStatus(User.Status.activated);

    when(tokenService.getUserIdFromToken(token)).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userMapper.toAuthResponse(user, "Email already verified. You can now log in."))
        .thenReturn(
            AuthResponse.builder()
                .userId(userId)
                .username("testuser")
                .email("test@example.com")
                .message("Email already verified. You can now log in.")
                .build());

    AuthResponse response = userService.verifyEmail(token);

    assertThat(response.getMessage()).contains("already verified");
    verify(userRepository, never()).save(any());
  }

  @Test
  void verifyEmail_expiredToken_throwsException() {
    when(tokenService.getUserIdFromToken(token))
        .thenThrow(new ExpiredTokenException("Token expired"));

    assertThatThrownBy(() -> userService.verifyEmail(token))
        .isInstanceOf(ExpiredTokenException.class);
  }

  @Test
  void verifyEmail_invalidToken_throwsException() {
    when(tokenService.getUserIdFromToken(token))
        .thenThrow(new InvalidTokenException("Invalid token"));

    assertThatThrownBy(() -> userService.verifyEmail(token))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void verifyEmail_userNotFound_throwsException() {
    when(tokenService.getUserIdFromToken(token)).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.verifyEmail(token))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");
  }
}
