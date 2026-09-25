package com.vegalife.repository.outbound;

import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import java.time.Instant;
import java.util.Collection;
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
}
