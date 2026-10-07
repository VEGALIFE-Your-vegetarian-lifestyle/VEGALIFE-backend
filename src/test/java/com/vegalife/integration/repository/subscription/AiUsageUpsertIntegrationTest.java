package com.vegalife.integration.repository.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.model.subscription.AiUsage;
import com.vegalife.model.user.User;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.repository.user.UserRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the atomic quota upsert on real PostgreSQL: {@code INSERT ... ON CONFLICT DO UPDATE}
 * must create the window row on the first send and increment it on every later send, so concurrent
 * turns cannot race past the limit.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class AiUsageUpsertIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("vegalife_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false);

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }

  @Autowired private AiUsageRepository usageRepository;
  @Autowired private UserRepository userRepository;

  private UUID userId;
  private Instant windowStart;
  private Instant windowEnd;

  @BeforeEach
  void setUp() {
    User user =
        userRepository.save(
            User.builder()
                .username("aiusage" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    userId = user.getId();

    YearMonth month = YearMonth.now(ZoneOffset.UTC);
    windowStart = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    windowEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  @Test
  void incrementRequestCount_createsRowThenIncrementsAtomically() {
    assertThat(usageRepository.sumRequestCountInWindow(userId, windowStart, windowEnd)).isZero();

    usageRepository.incrementRequestCount(userId, windowStart, windowEnd);
    usageRepository.incrementRequestCount(userId, windowStart, windowEnd);
    usageRepository.incrementRequestCount(userId, windowStart, windowEnd);

    assertThat(usageRepository.sumRequestCountInWindow(userId, windowStart, windowEnd))
        .isEqualTo(3L);
    assertThat(usageRepository.findAll())
        .filteredOn(u -> userId.equals(u.getUserId()))
        .singleElement()
        .extracting(AiUsage::getRequestCount)
        .isEqualTo(3);
  }

  @Test
  void sumRequestCountInWindow_ignoresOtherWindows() {
    Instant nextStart = windowEnd;
    Instant nextEnd = nextStart.plusSeconds(2_592_000);
    usageRepository.incrementRequestCount(userId, windowStart, windowEnd);
    usageRepository.incrementRequestCount(userId, nextStart, nextEnd);

    assertThat(usageRepository.sumRequestCountInWindow(userId, windowStart, windowEnd))
        .isEqualTo(1L);
  }

  @Test
  void sumRequestCountInWindow_withoutRowIsZero() {
    assertThat(usageRepository.sumRequestCountInWindow(UUID.randomUUID(), windowStart, windowEnd))
        .isZero();
  }
}
