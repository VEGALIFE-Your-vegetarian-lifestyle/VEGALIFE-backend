package com.vegalife.controller.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.response.auth.AuthResponse;
import com.vegalife.service.user.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private UserService userService;

  @Test
  void register_validRequest_returns200() throws Exception {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("testuser");
    request.setEmail("test@example.com");
    request.setPassword("password123");
    request.setConfirmPassword("password123");

    UUID userId = UUID.randomUUID();
    AuthResponse response =
        AuthResponse.builder()
            .userId(userId)
            .username("testuser")
            .email("test@example.com")
            .message("Verification email sent. Please check your inbox.")
            .build();

    when(userService.register(any(RegisterRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.username").value("testuser"))
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(
            jsonPath("$.message").value("Verification email sent. Please check your inbox."));
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
        .andExpect(status().isBadRequest());
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
        .andExpect(status().isBadRequest());
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
        .andExpect(status().isBadRequest());
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
        .andExpect(status().isBadRequest());
  }

  @Test
  void register_duplicateEmail_returns409() throws Exception {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("testuser");
    request.setEmail("test@example.com");
    request.setPassword("password123");
    request.setConfirmPassword("password123");

    when(userService.register(any(RegisterRequest.class)))
        .thenThrow(
            new com.vegalife.shared.exception.DuplicateResourceException(
                "Email already registered"));

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  @Test
  void verifyEmail_validToken_returns200() throws Exception {
    UUID userId = UUID.randomUUID();
    AuthResponse response =
        AuthResponse.builder()
            .userId(userId)
            .username("testuser")
            .email("test@example.com")
            .message("Email verified successfully. You can now log in.")
            .build();

    when(userService.verifyEmail(anyString())).thenReturn(response);

    mockMvc
        .perform(get("/api/auth/verify-email").param("token", "valid.token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.message").value("Email verified successfully. You can now log in."));
  }

  @Test
  void verifyEmail_invalidToken_returns400() throws Exception {
    when(userService.verifyEmail(anyString()))
        .thenThrow(new com.vegalife.shared.exception.InvalidTokenException("Invalid token"));

    mockMvc
        .perform(get("/api/auth/verify-email").param("token", "invalid"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void verifyEmail_expiredToken_returns400() throws Exception {
    when(userService.verifyEmail(anyString()))
        .thenThrow(new com.vegalife.shared.exception.ExpiredTokenException("Token expired"));

    mockMvc
        .perform(get("/api/auth/verify-email").param("token", "expired"))
        .andExpect(status().isBadRequest());
  }
}
