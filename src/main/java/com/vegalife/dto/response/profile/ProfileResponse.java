package com.vegalife.dto.response.profile;

import java.math.BigDecimal;
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
public class ProfileResponse {

  private UUID id;
  private UUID userId;
  private BigDecimal heightCm;
  private BigDecimal weightKg;
  private Integer age;
  private String gender;
  private String description;
  private String avatarUrl;
  private Instant updatedAt;
}
