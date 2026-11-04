package com.vegalife.dto.request.subscription;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One request for both acquisition modes (issue #181, ADR-021). The plan's price decides what
 * happens: {@code 0} is claimed, anything greater opens a checkout.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcquirePlanRequest {

  @NotBlank(message = "Plan code is required")
  private String planCode;
}
