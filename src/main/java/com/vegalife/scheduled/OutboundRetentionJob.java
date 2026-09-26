package com.vegalife.scheduled;

import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.shared.config.OutboundMessageProperties;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily retention purge for the outbound queue (ADR-005): deletes rows that reached a terminal
 * status before the {@code app.outbound.retention} cutoff, bounding how long even cleared payload
 * metadata stays in the table.
 */
@Component
@Slf4j
public class OutboundRetentionJob {

  private static final List<OutboundStatus> TERMINAL_STATUSES =
      List.of(OutboundStatus.COMPLETED, OutboundStatus.FAILED, OutboundStatus.EXPIRED);

  private final OutboundMessageRepository outboundMessageRepository;
  private final OutboundMessageProperties properties;
  private final boolean schedulingEnabled;

  public OutboundRetentionJob(
      OutboundMessageRepository outboundMessageRepository,
      OutboundMessageProperties properties,
      @Value("${app.scheduling.enabled:true}") boolean schedulingEnabled) {
    this.outboundMessageRepository = outboundMessageRepository;
    this.properties = properties;
    this.schedulingEnabled = schedulingEnabled;
  }

  @Scheduled(cron = "0 0 4 * * *") // Daily at 4:00 AM, after TokenCleanupJob and OtpCleanupJob
  public void purgeTerminalMessages() {
    if (!schedulingEnabled) {
      log.debug("Outbound retention purge skipped: app.scheduling.enabled=false");
      return;
    }
    Instant cutoff = Instant.now().minus(properties.getRetention());
    int purged = outboundMessageRepository.deleteTerminalOlderThan(TERMINAL_STATUSES, cutoff);
    log.info(
        "Outbound retention purge removed {} terminal messages older than {}",
        purged,
        properties.getRetention());
  }
}
