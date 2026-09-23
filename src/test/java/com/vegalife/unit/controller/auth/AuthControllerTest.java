package com.vegalife.unit.controller.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.request.auth.LoginRequest;
import com.vegalife.dto.request.auth.RefreshTokenRequest;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.response.auth.LoginResponse;
import com.vegalife.dto.response.auth.RegisterResponse;
import com.vegalife.service.auth.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AuthService authService;

  @Test
  void register_validRequest_returns201() throws Exception {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("testuser");
    request.setEmail("test@example.com");
    request.setPassword("password123");
    request.setConfirmPassword("password123");

    UUID userId = UUID.randomUUID();
    RegisterResponse response =
        RegisterResponse.builder()
            .userId(userId)
            .username("testuser")
            .email("test@example.com")
            .build();

    when(authService.register(any(RegisterRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("User registered successfully"))
        .andExpect(jsonPath("$.data.userId").value(userId.toString()))
        .andExpect(jsonPath("$.data.username").value("testuser"))
        .andExpect(jsonPath("$.data.email").value("test@example.com"));
  }

  @Test
  void register_missingUsername_returns400() throws Exception {
    RegisterRequest request = new RegisterRequest();
    request.setEmail("test@example.com");
    request.setPassword("password123");
    request.setConfirmPassword("password123");

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void register_invalidEmail_returns400() throws Exception {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("testuser");
    request.setEmail("invalid-email");
    request.setPassword("password123");
    request.setConfirmPassword("password123");

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void register_shortPassword_returns400() throws Exception {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("testuser");
    request.setEmail("test@example.com");
    request.setPassword("short");
    request.setConfirmPassword("short");

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void register_passwordMismatch_returns400() throws Exception {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("testuser");
    request.setEmail("test@example.com");
    request.setPassword("password123");
    request.setConfirmPassword("different");

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void register_duplicateEmail_returns409() throws Exception {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("testuser");
    request.setEmail("test@example.com");
    request.setPassword("password123");
    request.setConfirmPassword("password123");

    when(authService.register(any(RegisterRequest.class)))
        .thenThrow(
            new com.vegalife.shared.exception.DuplicateResourceException(
                "Email already registered"));

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Email already registered"));
  }

  @Test
  void verifyEmail_validToken_returns200() throws Exception {
    UUID userId = UUID.randomUUID();
    RegisterResponse response =
        RegisterResponse.builder()
            .userId(userId)
            .username("testuser")
            .email("test@example.com")
            .build();

    when(authService.verifyEmail(anyString())).thenReturn(response);

    mockMvc
        .perform(get("/api/auth/verify-email").param("token", "valid.token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Email verified successfully"))
        .andExpect(jsonPath("$.data.userId").value(userId.toString()))
        .andExpect(jsonPath("$.data.username").value("testuser"))
        .andExpect(jsonPath("$.data.email").value("test@example.com"));
  }

  @Test
  void verifyEmail_invalidToken_returns400() throws Exception {
    when(authService.verifyEmail(anyString()))
        .thenThrow(new com.vegalife.shared.exception.InvalidTokenException("Invalid token"));

    mockMvc
        .perform(get("/api/auth/verify-email").param("token", "invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Invalid token"));
  }

  @Test
  void verifyEmail_expiredToken_returns400() throws Exception {
    when(authService.verifyEmail(anyString()))
        .thenThrow(new com.vegalife.shared.exception.ExpiredTokenException("Token expired"));

    mockMvc
        .perform(get("/api/auth/verify-email").param("token", "expired"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Token expired"));
  }

  @Test
  void login_validRequest_returns200() throws Exception {
    LoginRequest request = new LoginRequest();
    request.setIdentifier("testuser");
    request.setPassword("password123");

    UUID userId = UUID.randomUUID();
    LoginResponse response =
        LoginResponse.builder()
            .userId(userId)
            .username("testuser")
            .email("test@example.com")
            .accessToken("access.token")
            .refreshToken("refresh.token")
            .tokenType("Bearer")
            .expiresIn(900L)
            .build();

    when(authService.login(any(LoginRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Login successful"))
        .andExpect(jsonPath("$.data.userId").value(userId.toString()))
        .andExpect(jsonPath("$.data.username").value("testuser"))
        .andExpect(jsonPath("$.data.email").value("test@example.com"))
        .andExpect(jsonPath("$.data.accessToken").value("access.token"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh.token"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(900));
  }

  @Test
  void login_invalidCredentials_returns400() throws Exception {
    LoginRequest request = new LoginRequest();
    request.setIdentifier("testuser");
    request.setPassword("wrongpassword");

    when(authService.login(any(LoginRequest.class)))
        .thenThrow(
            new com.vegalife.shared.exception.InvalidTokenException(
                "Invalid email/username or password"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Invalid email/username or password"));
  }

  @Test
  void login_unverifiedEmail_returns400() throws Exception {
    LoginRequest request = new LoginRequest();
    request.setIdentifier("testuser");
    request.setPassword("password123");

    when(authService.login(any(LoginRequest.class)))
        .thenThrow(
            new com.vegalife.shared.exception.InvalidTokenException(
                "Email not verified. Please verify your email before logging in."));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(
            jsonPath("$.message")
                .value("Email not verified. Please verify your email before logging in."));
  }

  @Test
  void refresh_validToken_returns200() throws Exception {
    RefreshTokenRequest request = new RefreshTokenRequest();
    request.setRefreshToken("valid.refresh.token");

    UUID userId = UUID.randomUUID();
    LoginResponse response =
        LoginResponse.builder()
            .accessToken("new.access.token")
            .tokenType("Bearer")
            .expiresIn(900L)
            .build();

    when(authService.refreshToken(anyString())).thenReturn(response);

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Token refreshed successfully"))
        .andExpect(jsonPath("$.data.accessToken").value("new.access.token"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(900));
  }

  @Test
  void refresh_missingToken_returns400() throws Exception {
    RefreshTokenRequest request = new RefreshTokenRequest();
    request.setRefreshToken("");

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void refresh_invalidToken_returns400() throws Exception {
    RefreshTokenRequest request = new RefreshTokenRequest();
    request.setRefreshToken("invalid.token");

    when(authService.refreshToken(anyString()))
        .thenThrow(
            new com.vegalife.shared.exception.InvalidTokenException("Invalid refresh token"));

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Invalid refresh token"));
  }

  @Test
  void logout_validToken_returns200() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer valid.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Logged out successfully"));
  }

  @Test
  void logout_missingToken_returns200() throws Exception {
    mockMvc
        .perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Logged out successfully"));
  }

  @Test
  void forgotPassword_validEmail_returns200_genericMessage() throws Exception {
    com.vegalife.dto.request.auth.ForgotPasswordRequest request =
        new com.vegalife.dto.request.auth.ForgotPasswordRequest();
    request.setEmail("test@example.com");

    org.mockito.Mockito.doNothing()
        .when(authService)
        .forgotPassword(any(com.vegalife.dto.request.auth.ForgotPasswordRequest.class));

    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "If an account with that email exists, a password reset code has been sent"));
  }

  @Test
  void forgotPassword_invalidEmail_returns400() throws Exception {
    com.vegalife.dto.request.auth.ForgotPasswordRequest request =
        new com.vegalife.dto.request.auth.ForgotPasswordRequest();
    request.setEmail("not-an-email");

    mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void resetPassword_validRequest_returns200() throws Exception {
    com.vegalife.dto.request.auth.ResetPasswordRequest request =
        new com.vegalife.dto.request.auth.ResetPasswordRequest();
    request.setEmail("test@example.com");
    request.setOtp("482913");
    request.setNewPassword("newPassword123");

    org.mockito.Mockito.doNothing()
        .when(authService)
        .resetPassword(any(com.vegalife.dto.request.auth.ResetPasswordRequest.class));

    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Password has been reset successfully"));
  }

  @Test
  void resetPassword_invalidOtp_returns400() throws Exception {
    com.vegalife.dto.request.auth.ResetPasswordRequest request =
        new com.vegalife.dto.request.auth.ResetPasswordRequest();
    request.setEmail("test@example.com");
    request.setOtp("000000");
    request.setNewPassword("newPassword123");

    org.mockito.Mockito.doThrow(
            new com.vegalife.shared.exception.InvalidTokenException(
                "Invalid or already used password reset code"))
        .when(authService)
        .resetPassword(any(com.vegalife.dto.request.auth.ResetPasswordRequest.class));

    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Invalid or already used password reset code"));
  }

  @Test
  void resetPassword_expiredOtp_returns400() throws Exception {
    com.vegalife.dto.request.auth.ResetPasswordRequest request =
        new com.vegalife.dto.request.auth.ResetPasswordRequest();
    request.setEmail("test@example.com");
    request.setOtp("482913");
    request.setNewPassword("newPassword123");

    org.mockito.Mockito.doThrow(
            new com.vegalife.shared.exception.ExpiredTokenException(
                "Password reset code has expired. Please request a new one."))
        .when(authService)
        .resetPassword(any(com.vegalife.dto.request.auth.ResetPasswordRequest.class));

    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(
            jsonPath("$.message")
                .value("Password reset code has expired. Please request a new one."));
  }

  @Test
  void resetPassword_shortPassword_returns400() throws Exception {
    com.vegalife.dto.request.auth.ResetPasswordRequest request =
        new com.vegalife.dto.request.auth.ResetPasswordRequest();
    request.setEmail("test@example.com");
    request.setOtp("482913");
    request.setNewPassword("short");

    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void resetPassword_nonDigitOtp_returns400() throws Exception {
    com.vegalife.dto.request.auth.ResetPasswordRequest request =
        new com.vegalife.dto.request.auth.ResetPasswordRequest();
    request.setEmail("test@example.com");
    request.setOtp("abcdef");
    request.setNewPassword("newPassword123");

    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }
}
