package com.vegalife.enums.subscription;

/** What {@code POST /api/subscriptions/acquire} did for the caller (issue #181, ADR-021). */
public enum AcquireAction {

  /** A free plan was granted immediately. No money moved. */
  CLAIMED,

  /** A paid plan opened a gateway checkout. The grant arrives with the payment notification. */
  CHECKOUT_CREATED
}
