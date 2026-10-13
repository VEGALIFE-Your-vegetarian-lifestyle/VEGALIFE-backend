package com.vegalife.dto.response.profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

  private UUID userId;
  private String username;
  private String email;
  private BigDecimal heightCm;
  private BigDecimal weightKg;
  private Integer age;
  private String gender;
  private String description;
  private String avatarUrl;
  private List<String> preferences;
  private List<String> dislikes;
  private List<String> allergies;
  private List<String> restrictions;
  private String healthGoal;
  private BigDecimal bmi;
  private Instant updatedAt;
}
