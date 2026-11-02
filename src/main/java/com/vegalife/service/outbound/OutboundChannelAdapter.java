package com.vegalife.service.outbound;

import com.vegalife.enums.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;

/**
 * Delivery seam per channel (ADR-005). The drainer resolves the adapter for a claimed row's
 * channel; an adapter either delivers, throws to ask for a retry, or throws to report a permanent
 * failure the drainer should abandon.
 */
public interface OutboundChannelAdapter {

  OutboundChannel channel();

  /**
   * Delivers the claimed message.
   *
   * @throws com.vegalife.shared.exception.RetryableDeliveryException when the failure is transient
   *     and the attempt should be retried, honouring its {@code retryAfter} hint when present
   * @throws RuntimeException for a permanent failure: the drainer abandons the message and calls
   *     {@link #onDeliveryAbandoned} rather than retrying (ADR-020)
   */
  void deliver(OutboundMessage message);

  /**
   * Called when a message for this channel is abandoned without delivery — a permanent rejection,
   * or a retry that could not be scheduled inside the queue's max age.
   *
   * <p>Optional hook, and deliberately a default no-op: the drainer must not know what a channel's
   * message means. An adapter whose failure has a domain consequence (the content filter queues the
   * post for review) implements this; adapters with none ignore it.
   */
  default void onDeliveryAbandoned(OutboundMessage message, RuntimeException cause) {}
}
