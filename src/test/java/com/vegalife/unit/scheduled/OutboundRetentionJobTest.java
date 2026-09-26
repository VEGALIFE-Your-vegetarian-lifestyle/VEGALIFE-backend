package com.vegalife.unit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.scheduled.OutboundRetentionJob;
import com.vegalife.shared.config.OutboundMessageProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboundRetentionJobTest {

  @Mock private OutboundMessageRepository outboundMessageRepository;

  private OutboundMessageProperties properties;

  @BeforeEach
  void setUp() {
    properties = new OutboundMessageProperties();
  }

  @Test
  void purgeTerminalMessages_deletesTerminalRowsPastRetentionCutoff() {
    when(outboundMessageRepository.deleteTerminalOlderThan(any(), any(Instant.class)))
        .thenReturn(3);
    OutboundRetentionJob job =
        new OutboundRetentionJob(outboundMessageRepository, properties, true);

    job.purgeTerminalMessages();

    ArgumentCaptor<List<OutboundStatus>> statusesCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(outboundMessageRepository)
        .deleteTerminalOlderThan(statusesCaptor.capture(), cutoffCaptor.capture());

    assertThat(statusesCaptor.getValue())
        .containsExactlyInAnyOrder(
            OutboundStatus.COMPLETED, OutboundStatus.FAILED, OutboundStatus.EXPIRED);
    Instant expectedCutoff = Instant.now().minus(properties.getRetention());
    assertThat(cutoffCaptor.getValue())
        .isBetween(expectedCutoff.minusSeconds(5), expectedCutoff.plusSeconds(5));
  }

  @Test
  void purgeTerminalMessages_whenSchedulingDisabled_touchesNothing() {
    OutboundRetentionJob job =
        new OutboundRetentionJob(outboundMessageRepository, properties, false);

    job.purgeTerminalMessages();

    verifyNoInteractions(outboundMessageRepository);
  }
}
