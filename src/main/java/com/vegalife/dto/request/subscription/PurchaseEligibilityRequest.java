package com.vegalife.dto.request.subscription;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseEligibilityRequest {

  @NotBlank(message = "Plan code must not be blank")
  private String planCode;
}
