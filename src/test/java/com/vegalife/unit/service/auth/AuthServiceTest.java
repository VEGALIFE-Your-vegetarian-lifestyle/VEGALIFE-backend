package com.vegalife.unit.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.auth.AuthMapper;
import com.vegalife.dto.mapper.auth.LoginMapper;
import com.vegalife.dto.request.auth.LoginRequest;
import com.vegalife.dto.request.auth.RefreshTokenRequest;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.response.auth.LoginResponse;
import com.vegalife.dto.response.auth.RegisterResponse;
import com.vegalife.model.token.RefreshToken;
import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.auth.AuthService;
import com.vegalife.service.email.EmailService;
import com.vegalife.service.token.JwtTokenService;
import com.vegalife.service.token.VerificationTokenService;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ExpiredTokenException;
import com.vegalife.shared.exception.InvalidTokenException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private AuthMapper authMapper;
  @Mock private LoginMapper loginMapper;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private VerificationTokenService tokenService;

  @Mock(lenient = true)
  private JwtTokenService jwtTokenService;

  @Mock private EmailService emailService;

  @InjectMocks private AuthService authService;

  private RegisterRequest validRegisterRequest;
  private LoginRequest validLoginRequest;
  private RefreshTokenRequest validRefreshRequest;
  private User user;
  private String encodedPassword;
  private String accessToken;
  private String refreshToken;
  private String token;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    validRegisterRequest = new RegisterRequest();
    validRegisterRequest.setUsername("testuser");
    validRegisterRequest.setEmail("test@example.com");
    validRegisterRequest.setPassword("password123");
    validRegisterRequest.setConfirmPassword("password123");

    validLoginRequest = new LoginRequest();
    validLoginRequest.setIdentifier("testuser");
    validLoginRequest.setPassword("password123");

    validRefreshRequest = new RefreshTokenRequest();
    validRefreshRequest.setRefreshToken("valid.refresh.token");

    user =
        User.builder()
            .id(userId)
            .username("testuser")
            .email("test@example.com")
            .passwordHash("encoded")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();

    encodedPassword = "encodedPassword123";
    accessToken = "access.token.here";
    refreshToken = "refresh.token.here";
    token = "valid.jwt.token";

    // Set private fields using ReflectionTestUtils
    ReflectionTestUtils.setField(authService, "baseUrl", "http://localhost:8080");
    ReflectionTestUtils.setField(jwtTokenService, "accessTokenExpiryMinutes", 15L);
    when(jwtTokenService.getAccessTokenExpirySeconds()).thenReturn(900L);
  }

  @Test
  void register_success() {
    when(userRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(false);
    when(userRepository.existsByUsername(validRegisterRequest.getUsername())).thenReturn(false);
    when(passwordEncoder.encode(validRegisterRequest.getPassword())).thenReturn(encodedPassword);
    when(authMapper.toEntity(validRegisterRequest, encodedPassword)).thenReturn(user);
    when(userRepository.save(any(User.class))).thenReturn(user);
    when(tokenService.generateToken(user)).thenReturn(token);
    when(authMapper.toRegisterResponse(user))
        .thenReturn(
            RegisterResponse.builder()
                .userId(userId)
                .username("testuser")
                .email("test@example.com")
                .build());

    RegisterResponse response = authService.register(validRegisterRequest);

    assertThat(response).isNotNull();
    assertThat(response.getUserId()).isEqualTo(userId);
    assertThat(response.getUsername()).isEqualTo("testuser");
    assertThat(response.getEmail()).isEqualTo("test@example.com");

    verify(userRepository).save(user);
    verify(tokenService).generateToken(user);
    verify(emailService).sendVerificationEmail(eq("test@example.com"), eq("testuser"), anyString());
  }

  @Test
  void register_duplicateEmail_throwsException() {
    when(userRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(true);

    assertThatThrownBy(() -> authService.register(validRegisterRequest))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Email already registered");

    verify(userRepository, never()).save(any());
  }

  @Test
  void register_duplicateUsername_throwsException() {
    when(userRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(false);
    when(userRepository.existsByUsername(validRegisterRequest.getUsername())).thenReturn(true);

    assertThatThrownBy(() -> authService.register(validRegisterRequest))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Username already taken");
  }

  @Test
  void verifyEmail_success() {
    // Use a non-verified user for this test
    user.setEmailVerified(false);
    user.setStatus(User.Status.created);

    when(tokenService.getUserIdFromToken(token)).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenReturn(user);
    when(authMapper.toRegisterResponse(user))
        .thenReturn(
            RegisterResponse.builder()
                .userId(userId)
                .username("testuser")
                .email("test@example.com")
                .build());

    RegisterResponse response = authService.verifyEmail(token);

    assertThat(response).isNotNull();
    assertThat(user.getEmailVerified()).isTrue();
    assertThat(user.getStatus()).isEqualTo(User.Status.activated);
  }

  @Test
  void verifyEmail_alreadyVerified_returnsSuccess() {
    user.setEmailVerified(true);
    user.setStatus(User.Status.activated);

    when(tokenService.getUserIdFromToken(token)).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(authMapper.toRegisterResponse(user))
        .thenReturn(
            RegisterResponse.builder()
                .userId(userId)
                .username("testuser")
                .email("test@example.com")
                .build());

    RegisterResponse response = authService.verifyEmail(token);

    assertThat(response).isNotNull();
    verify(userRepository, never()).save(any());
  }

  @Test
  void verifyEmail_expiredToken_throwsException() {
    when(tokenService.getUserIdFromToken(token))
        .thenThrow(new ExpiredTokenException("Token expired"));

    assertThatThrownBy(() -> authService.verifyEmail(token))
        .isInstanceOf(ExpiredTokenException.class);
  }

  @Test
  void verifyEmail_invalidToken_throwsException() {
    when(tokenService.getUserIdFromToken(token))
        .thenThrow(new InvalidTokenException("Invalid token"));

    assertThatThrownBy(() -> authService.verifyEmail(token))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void verifyEmail_userNotFound_throwsException() {
    when(tokenService.getUserIdFromToken(token)).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.verifyEmail(token))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");
  }

  @Test
  void login_success() {
    when(userRepository.findByEmail(validLoginRequest.getIdentifier()))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(validLoginRequest.getPassword(), user.getPasswordHash()))
        .thenReturn(true);
    when(jwtTokenService.generateAccessToken(user)).thenReturn(accessToken);
    when(jwtTokenService.generateRefreshToken(user)).thenReturn(refreshToken);
    when(userRepository.save(any(User.class))).thenReturn(user);
    when(loginMapper.toLoginResponse(user))
        .thenReturn(
            LoginResponse.builder()
                .userId(userId)
                .username("testuser")
                .email("test@example.com")
                .build());

    LoginResponse response = authService.login(validLoginRequest);

    assertThat(response).isNotNull();
    assertThat(response.getAccessToken()).isEqualTo(accessToken);
    assertThat(response.getRefreshToken()).isEqualTo(refreshToken);
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    assertThat(response.getExpiresIn()).isEqualTo(900L);
    verify(userRepository).save(user);
  }

  @Test
  void login_userNotFound_throwsException() {
    when(userRepository.findByEmail(validLoginRequest.getIdentifier()))
        .thenReturn(Optional.empty());
    when(userRepository.findByUsername(validLoginRequest.getIdentifier()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(validLoginRequest))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Invalid email/username or password");
  }

  @Test
  void login_invalidPassword_throwsException() {
    when(userRepository.findByEmail(validLoginRequest.getIdentifier()))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(validLoginRequest.getPassword(), user.getPasswordHash()))
        .thenReturn(false);

    assertThatThrownBy(() -> authService.login(validLoginRequest))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Invalid email/username or password");
  }

  @Test
  void login_unverifiedEmail_throwsException() {
    user.setEmailVerified(false);
    when(userRepository.findByEmail(validLoginRequest.getIdentifier()))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(validLoginRequest.getPassword(), user.getPasswordHash()))
        .thenReturn(true);

    assertThatThrownBy(() -> authService.login(validLoginRequest))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Email not verified. Please verify your email before logging in.");
  }

  @Test
  void login_inactiveStatus_throwsException() {
    user.setStatus(User.Status.deactivated);
    when(userRepository.findByEmail(validLoginRequest.getIdentifier()))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(validLoginRequest.getPassword(), user.getPasswordHash()))
        .thenReturn(true);

    assertThatThrownBy(() -> authService.login(validLoginRequest))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Account is not active");
  }

  @Test
  void login_suspendedStatus_throwsException() {
    user.setStatus(User.Status.suspended);
    when(userRepository.findByEmail(validLoginRequest.getIdentifier()))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(validLoginRequest.getPassword(), user.getPasswordHash()))
        .thenReturn(true);

    assertThatThrownBy(() -> authService.login(validLoginRequest))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Account is suspended");
  }

  @Test
  void refreshToken_nonActivatedAccount_throwsException() {
    user.setStatus(User.Status.suspended);
    RefreshToken storedToken =
        RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .tokenHash("hashed.token.value")
            .expiresAt(Instant.now().plusSeconds(604800))
            .build();

    when(jwtTokenService.validateRefreshToken(refreshToken)).thenReturn(storedToken);

    assertThatThrownBy(() -> authService.refreshToken(refreshToken))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Account is not active");
  }

  @Test
  void login_usernameIdentifier_success() {
    when(userRepository.findByEmail(validLoginRequest.getIdentifier()))
        .thenReturn(Optional.empty());
    when(userRepository.findByUsername(validLoginRequest.getIdentifier()))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(validLoginRequest.getPassword(), user.getPasswordHash()))
        .thenReturn(true);
    when(jwtTokenService.generateAccessToken(user)).thenReturn(accessToken);
    when(jwtTokenService.generateRefreshToken(user)).thenReturn(refreshToken);
    when(userRepository.save(any(User.class))).thenReturn(user);
    when(loginMapper.toLoginResponse(user))
        .thenReturn(
            LoginResponse.builder()
                .userId(userId)
                .username("testuser")
                .email("test@example.com")
                .build());

    LoginRequest usernameRequest = new LoginRequest();
    usernameRequest.setIdentifier("testuser");
    usernameRequest.setPassword("password123");

    LoginResponse response = authService.login(usernameRequest);

    assertThat(response).isNotNull();
    assertThat(response.getAccessToken()).isEqualTo(accessToken);
    assertThat(response.getRefreshToken()).isEqualTo(refreshToken);
  }

  @Test
  void refreshToken_success() {
    RefreshToken storedToken =
        RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .tokenHash("hashed.token.value")
            .expiresAt(Instant.now().plusSeconds(604800))
            .build();

    when(jwtTokenService.validateRefreshToken(refreshToken)).thenReturn(storedToken);
    when(jwtTokenService.generateAccessToken(user)).thenReturn(accessToken);
    when(loginMapper.toLoginResponse(user))
        .thenReturn(
            LoginResponse.builder()
                .userId(userId)
                .username("testuser")
                .email("test@example.com")
                .build());

    LoginResponse response = authService.refreshToken(refreshToken);

    assertThat(response).isNotNull();
    assertThat(response.getAccessToken()).isEqualTo(accessToken);
    assertThat(response.getRefreshToken()).isEqualTo(refreshToken);
    assertThat(response.getTokenType()).isEqualTo("Bearer");
  }

  @Test
  void refreshToken_expired_throwsException() {
    when(jwtTokenService.validateRefreshToken(refreshToken))
        .thenThrow(new ExpiredTokenException("Refresh token has expired"));

    assertThatThrownBy(() -> authService.refreshToken(refreshToken))
        .isInstanceOf(ExpiredTokenException.class)
        .hasMessage("Refresh token has expired");
  }

  @Test
  void refreshToken_revoked_throwsException() {
    when(jwtTokenService.validateRefreshToken(refreshToken))
        .thenThrow(new InvalidTokenException("Refresh token has been revoked"));

    assertThatThrownBy(() -> authService.refreshToken(refreshToken))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Refresh token has been revoked");
  }

  @Test
  void refreshToken_invalid_throwsException() {
    when(jwtTokenService.validateRefreshToken(refreshToken))
        .thenThrow(new InvalidTokenException("Invalid refresh token"));

    assertThatThrownBy(() -> authService.refreshToken(refreshToken))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Invalid refresh token");
  }

  @Test
  void logout_success() {
    String accessToken = "valid.access.token";
    String jti = UUID.randomUUID().toString();
    String issuer = "vegalife-backend";

    when(jwtTokenService.extractJti(accessToken)).thenReturn(jti);
    when(jwtTokenService.extractIssuer(accessToken)).thenReturn(issuer);
    when(jwtTokenService.extractExpiration(accessToken)).thenReturn(Instant.now().plusSeconds(900));
    when(jwtTokenService.validateAccessTokenAndGetUserId(accessToken)).thenReturn(userId);

    authService.logout(accessToken);

    verify(jwtTokenService)
        .blacklistAccessToken(eq(jti), eq(issuer), ArgumentMatchers.any(Instant.class));
    verify(jwtTokenService).revokeAllUserRefreshTokens(userId);
  }

  @Test
  void logout_userNotFound_stillBlacklists() {
    String accessToken = "valid.access.token";
    String jti = UUID.randomUUID().toString();
    String issuer = "vegalife-backend";

    when(jwtTokenService.extractJti(accessToken)).thenReturn(jti);
    when(jwtTokenService.extractIssuer(accessToken)).thenReturn(issuer);
    when(jwtTokenService.extractExpiration(accessToken)).thenReturn(Instant.now().plusSeconds(900));
    when(jwtTokenService.validateAccessTokenAndGetUserId(accessToken)).thenReturn(userId);

    authService.logout(accessToken);

    verify(jwtTokenService)
        .blacklistAccessToken(eq(jti), eq(issuer), ArgumentMatchers.any(Instant.class));
    verify(jwtTokenService).revokeAllUserRefreshTokens(userId);
  }
}
