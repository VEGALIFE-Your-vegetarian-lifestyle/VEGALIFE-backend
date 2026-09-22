package com.vegalife.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("integration")
public class IntegrationTestConfig {
  // Configuration class for integration test profile
  // Testcontainers and DynamicPropertySource are in BaseIntegrationTest
}
