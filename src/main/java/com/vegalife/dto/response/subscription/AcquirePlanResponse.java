package com.vegalife.dto.response.subscription;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vegalife.enums.subscription.AcquireAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of {@code POST /api/subscriptions/acquire} (issue #181, ADR-021).
 *
 * <p>The checkout fields are present only when {@link #action} is {@code CHECKOUT_CREATED} and are
 * omitted from JSON otherwise. {@link #remainingUsage} is always present and is the only usage
 * number this API ever returns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcquirePlanResponse {

  private String planCode;

  private AcquireAction action;

  private long remainingUsage;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String paymentId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String txnRef;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String paymentUrl;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long amount;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String currency;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String status;
}
