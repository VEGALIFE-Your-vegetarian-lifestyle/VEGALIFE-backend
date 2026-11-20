package com.vegalife.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The server clock, injected where a rule depends on "today". Production uses UTC (BR-MENU-004);
 * tests replace the bean (or construct the owning service with a fixed {@link Clock}) so a date
 * rule can be asserted without waiting for midnight.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
