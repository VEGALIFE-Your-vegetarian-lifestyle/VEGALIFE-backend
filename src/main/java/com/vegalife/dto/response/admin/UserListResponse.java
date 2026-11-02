package com.vegalife.dto.response.admin;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListResponse {

  private UUID id;
  private String email;
  private String displayName;
  private String role;
  private String status;
  private Instant createdAt;
}
