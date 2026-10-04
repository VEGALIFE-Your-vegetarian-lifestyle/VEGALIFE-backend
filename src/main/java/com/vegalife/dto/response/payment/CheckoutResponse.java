package com.vegalife.dto.response.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {

  private String txnRef;
  private String planCode;
  private long amount;
  private String currency;
  private String status;
  private String paymentUrl;
}
