package com.vegalife.controller.auth;

import com.vegalife.dto.request.auth.ForgotPasswordRequest;
import com.vegalife.dto.request.auth.LoginRequest;
import com.vegalife.dto.request.auth.RefreshTokenRequest;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.request.auth.ResetPasswordRequest;
import com.vegalife.dto.response.auth.LoginResponse;
import com.vegalife.dto.response.auth.RegisterResponse;
import com.vegalife.service.auth.AuthService;
import com.vegalife.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<RegisterResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    RegisterResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "User registered successfully"));
  }

  @GetMapping("/verify-email")
  public ResponseEntity<ApiResponse<RegisterResponse>> verifyEmail(@RequestParam String token) {
    RegisterResponse response = authService.verifyEmail(token);
    return ResponseEntity.ok(ApiResponse.success(response, "Email verified successfully"));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<LoginResponse>> refresh(
      @Valid @RequestBody RefreshTokenRequest request) {
    LoginResponse response = authService.refreshToken(request.getRefreshToken());
    return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
    String authHeader = httpRequest.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String accessToken = authHeader.substring(7);
      authService.logout(accessToken);
    }
    return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<ApiResponse<Void>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request);
    return ResponseEntity.ok(
        ApiResponse.success(
            null, "If an account with that email exists, a password reset code has been sent"));
  }

  @PostMapping("/reset-password")
  public ResponseEntity<ApiResponse<Void>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return ResponseEntity.ok(ApiResponse.success(null, "Password has been reset successfully"));
  }
}
