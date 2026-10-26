package com.vegalife.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Inbound contract for {@code POST /api/auth/google} (ADR-017).
 *
 * <p>Carries the Google <em>authorization code</em>, not an ID token: the code is exchanged
 * server-side so the client secret stays on the server and the resulting token is provably minted
 * for this client.
 */
@Data
public class GoogleLoginRequest {

  @NotBlank(message = "Authorization code is required")
  private String code;
}
