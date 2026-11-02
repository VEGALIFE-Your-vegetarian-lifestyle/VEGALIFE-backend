package com.vegalife.dto.response.subscription;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

  private String planCode;
  private long amount;
  private String currency;
  private String status;
  private String provider;
  private Instant paidAt;
}
