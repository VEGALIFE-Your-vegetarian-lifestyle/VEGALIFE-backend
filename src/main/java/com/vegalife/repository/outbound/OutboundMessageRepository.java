package com.vegalife.repository.outbound;

import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OutboundMessageRepository extends JpaRepository<OutboundMessage, UUID> {

  @Modifying
  @Transactional
  @Query("DELETE FROM OutboundMessage o WHERE o.status IN :statuses AND o.createdAt < :cutoff")
  int deleteTerminalOlderThan(
      @Param("statuses") Collection<OutboundStatus> statuses, @Param("cutoff") Instant cutoff);

  /**
   * BR-FILTER-009 sweep input: recipients (post ids for CONTENT_FILTER) whose <em>latest</em>
   * message for the channel is older than the cutoff. A per-recipient MAX keeps a freshly re-queued
   * post out of the result even when an older message of the same post is stale.
   */
  @Query(
      "SELECT o.recipient FROM OutboundMessage o WHERE o.channel = :channel"
          + " GROUP BY o.recipient HAVING MAX(o.createdAt) < :cutoff")
  List<String> findRecipientsOfChannelWithLatestBefore(
      @Param("channel") OutboundChannel channel, @Param("cutoff") Instant cutoff);
}
