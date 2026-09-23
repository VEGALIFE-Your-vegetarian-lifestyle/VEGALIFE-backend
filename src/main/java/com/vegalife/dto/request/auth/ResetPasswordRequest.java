package com.vegalife.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

  @NotBlank(message = "Email is required")
  @Email(message = "Email must be valid")
  @Size(max = 100, message = "Email must not exceed 100 characters")
  private String email;

  @NotBlank(message = "OTP is required")
  @Pattern(regexp = "^\\d{6}$", message = "OTP must be a 6-digit code")
  private String otp;

  @NotBlank(message = "New password is required")
  @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
  private String newPassword;
}
