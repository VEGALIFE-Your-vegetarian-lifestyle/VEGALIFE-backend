package com.vegalife.enums.subscription;

/** How a member acquired a plan (issue #181, ADR-021). */
public enum AcquisitionSource {

  /** Bought through the payment gateway; the acquisition carries a {@code paymentId}. */
  PURCHASE,

  /** Granted for free by claiming; the acquisition has no {@code paymentId}. */
  CLAIM
}
