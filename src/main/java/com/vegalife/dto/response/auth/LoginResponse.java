package com.vegalife.dto.response.auth;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

  private UUID userId;
  private String username;
  private String email;
  private String accessToken;
  private String refreshToken;
  private String tokenType;
  private Long expiresIn;
}
