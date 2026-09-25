package com.vegalife.shared.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Tuning for the persistent outbound queue, bound from {@code app.outbound.*} (ADR-005). */
@Data
@Component
@ConfigurationProperties(prefix = "app.outbound")
public class OutboundMessageProperties {

  private Polling polling = new Polling();
  private Retry retry = new Retry();
  private Duration visibilityTimeout = Duration.ofSeconds(60);
  private Duration maxAge = Duration.ofHours(24);
  private Duration retention = Duration.ofDays(7);
  private int batchSize = 10;

  @Data
  public static class Polling {

    private Duration interval = Duration.ofSeconds(5);
  }

  @Data
  public static class Retry {

    /** Increasing fast-retry delays; one entry per retry after the initial attempt. */
    private List<Duration> delays =
        new ArrayList<>(
            List.of(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(2)));

    private Duration deferredDelay = Duration.ofMinutes(5);
  }
}
