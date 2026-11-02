package com.vegalife.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

  @NotBlank(message = "Identifier is required")
  private String identifier;

  @NotBlank(message = "Password is required")
  private String password;
}
