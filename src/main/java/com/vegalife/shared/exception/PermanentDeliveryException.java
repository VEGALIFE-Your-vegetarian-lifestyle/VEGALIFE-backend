package com.vegalife.shared.exception;

/**
 * A delivery failure that no retry can fix, so the queue should stop at once.
 *
 * <p>Raised by a channel adapter when the receiver has definitively refused the message — an
 * unknown recipient, a payload the receiver will never accept, a resource that no longer exists.
 * The drainer marks the message {@code FAILED} without consuming the retry ladder and calls {@link
 * com.vegalife.service.outbound.OutboundChannelAdapter#onDeliveryAbandoned} so the channel can
 * record the domain consequence (ADR-020).
 *
 * <p>Permanence is declared, never inferred: an adapter that throws anything else — including a
 * plain {@link RuntimeException} — is retried. A queue that treats an unclassified failure as
 * permanent silently drops work, and an SMTP or provider outage is indistinguishable from a
 * genuinely bad message until someone classifies it.
 */
public class PermanentDeliveryException extends RuntimeException {

  public PermanentDeliveryException(String message) {
    super(message);
  }

  public PermanentDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
