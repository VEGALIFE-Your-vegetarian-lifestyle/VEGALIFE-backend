package com.vegalife.service.outbound;

import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;

/**
 * Delivery seam per channel (ADR-005). The drainer resolves the adapter for a claimed row's
 * channel; an adapter either delivers or throws, and the drainer settles the row's state.
 */
public interface OutboundChannelAdapter {

  OutboundChannel channel();

  /**
   * Delivers the claimed message.
   *
   * @throws RuntimeException when delivery fails and the attempt should be retried
   */
  void deliver(OutboundMessage message);
}
