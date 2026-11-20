package com.vegalife.integration.repository.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.subscription.AiUsage;
import com.vegalife.model.user.User;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.repository.user.UserRepository;
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
 * Exercises the atomic consumption upsert on real PostgreSQL (issue #181): {@code INSERT ... ON
 * CONFLICT (user_id) DO UPDATE} creates the member's one lifetime row on the first send and
 * increments it in place forever after. No window, no reset.
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
      new PostgreSQLContainer<>("pgvector/pgvector:pg15")
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

  @BeforeEach
  void setUp() {
    User user =
        userRepository.save(
            User.builder()
                .displayName("aiusage" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.USER)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
    userId = user.getId();
  }

  @Test
  void incrementRequestCount_createsRowThenIncrementsAtomically() {
    assertThat(usageRepository.sumRequestCount(userId)).isZero();

    usageRepository.incrementRequestCount(userId);
    usageRepository.incrementRequestCount(userId);
    usageRepository.incrementRequestCount(userId);

    assertThat(usageRepository.sumRequestCount(userId)).isEqualTo(3L);
    assertThat(usageRepository.findAll())
        .filteredOn(u -> userId.equals(u.getUserId()))
        .singleElement()
        .extracting(AiUsage::getRequestCount)
        .isEqualTo(3);
  }

  @Test
  void incrementRequestCount_neverCreatesASecondRowForTheSameMember() {
    usageRepository.incrementRequestCount(userId);
    usageRepository.incrementRequestCount(userId);

    assertThat(usageRepository.findAll()).filteredOn(u -> userId.equals(u.getUserId())).hasSize(1);
  }

  @Test
  void sumRequestCount_withoutRowIsZero() {
    assertThat(usageRepository.sumRequestCount(UUID.randomUUID())).isZero();
  }
}
