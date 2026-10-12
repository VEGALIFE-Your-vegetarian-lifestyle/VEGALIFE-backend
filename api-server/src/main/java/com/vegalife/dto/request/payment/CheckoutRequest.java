package com.vegalife.dto.request.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckoutRequest {

  @NotBlank(message = "Plan code is required")
  private String planCode;
}
