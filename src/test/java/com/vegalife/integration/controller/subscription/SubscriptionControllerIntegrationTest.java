package com.vegalife.integration.controller.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.AiUsage;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.model.user.User;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class SubscriptionControllerIntegrationTest {

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

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private AiPlanRepository planRepository;

  @Autowired private AiSubscriptionRepository subscriptionRepository;

  @Autowired private AiUsageRepository usageRepository;

  @Autowired private PaymentLedgerRepository paymentLedgerRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User testUser;
  private String accessToken;

  @BeforeEach
  void setUp() {
    testUser =
        User.builder()
            .username("subuser")
            .email("sub@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    testUser = userRepository.save(testUser);

    accessToken = jwtTokenService.generateAccessToken(testUser);
  }

  @Test
  void getMySubscription_withoutAuthentication_returns401() throws Exception {
    mockMvc
        .perform(get("/api/subscriptions/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Unauthorized"));
  }

  @Test
  void getMySubscription_withoutSubscriptionRow_returnsFreeDefaultWithoutWriting()
      throws Exception {
    AiPlan freePlan = planRepository.findByCode("FREE").orElseThrow();
    YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
    Instant periodStart = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant periodEnd =
        currentMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    mockMvc
        .perform(get("/api/subscriptions/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Subscription retrieved successfully"))
        .andExpect(jsonPath("$.data.tier").value("FREE"))
        .andExpect(jsonPath("$.data.status").value("active"))
        .andExpect(jsonPath("$.data.renewalDate").doesNotExist())
        .andExpect(jsonPath("$.data.usage.used").value(0))
        .andExpect(jsonPath("$.data.usage.limit").value(freePlan.getMonthlyRequestLimit()))
        .andExpect(jsonPath("$.data.usage.periodStart").value(periodStart.toString()))
        .andExpect(jsonPath("$.data.usage.periodEnd").value(periodEnd.toString()))
        .andExpect(jsonPath("$.data.currentPlan.code").value("FREE"))
        .andExpect(jsonPath("$.data.currentPlan.name").value("Free"))
        .andExpect(
            jsonPath("$.data.currentPlan.monthlyRequestLimit")
                .value(freePlan.getMonthlyRequestLimit()))
        .andExpect(
            jsonPath("$.data.currentPlan.price.amount").value((int) freePlan.getPriceAmount()))
        .andExpect(jsonPath("$.data.currentPlan.price.currency").value(freePlan.getPriceCurrency()))
        .andExpect(jsonPath("$.data.latestPayment").doesNotExist());

    assertThat(subscriptionRepository.findByUserId(testUser.getId())).isEmpty();
  }

  @Test
  void getMySubscription_withSubscription_returnsQuotaWindowAndLatestSucceededPayment()
      throws Exception {
    AiPlan proPlan = planRepository.findByCode("PRO").orElseThrow();
    Instant renewalDate = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    subscriptionRepository.save(
        AiSubscription.builder()
            .userId(testUser.getId())
            .planId(proPlan.getId())
            .status(AiSubscription.Status.active)
            .renewalDate(renewalDate)
            .build());

    YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
    Instant periodStart = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant periodEnd =
        currentMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    // Row inside the current window: counted.
    usageRepository.save(
        AiUsage.builder()
            .userId(testUser.getId())
            .requestCount(3)
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .build());
    // Row ending exactly at the window start: outside the window, must not count.
    usageRepository.save(
        AiUsage.builder()
            .userId(testUser.getId())
            .requestCount(99)
            .periodStart(periodStart.minus(31, ChronoUnit.DAYS))
            .periodEnd(periodStart)
            .build());

    Instant latestPaidAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    paymentLedgerRepository.save(
        PaymentLedger.builder()
            .userId(testUser.getId())
            .planId(proPlan.getId())
            .amount(proPlan.getPriceAmount())
            .currency(proPlan.getPriceCurrency())
            .status(PaymentLedger.Status.succeeded)
            .provider("vnpay")
            .paidAt(latestPaidAt.minus(1, ChronoUnit.DAYS))
            .build());
    paymentLedgerRepository.save(
        PaymentLedger.builder()
            .userId(testUser.getId())
            .planId(proPlan.getId())
            .amount(proPlan.getPriceAmount())
            .currency(proPlan.getPriceCurrency())
            .status(PaymentLedger.Status.succeeded)
            .provider("vnpay")
            .paidAt(latestPaidAt)
            .build());
    // Newer but not succeeded: must be ignored by the latest-payment lookup.
    paymentLedgerRepository.save(
        PaymentLedger.builder()
            .userId(testUser.getId())
            .planId(proPlan.getId())
            .amount(proPlan.getPriceAmount())
            .currency(proPlan.getPriceCurrency())
            .status(PaymentLedger.Status.pending)
            .provider("vnpay")
            .paidAt(latestPaidAt.plus(1, ChronoUnit.MINUTES))
            .build());

    mockMvc
        .perform(get("/api/subscriptions/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.tier").value("PRO"))
        .andExpect(jsonPath("$.data.status").value("active"))
        .andExpect(jsonPath("$.data.renewalDate").value(renewalDate.toString()))
        .andExpect(jsonPath("$.data.usage.used").value(3))
        .andExpect(jsonPath("$.data.usage.limit").value(proPlan.getMonthlyRequestLimit()))
        .andExpect(jsonPath("$.data.usage.periodStart").value(periodStart.toString()))
        .andExpect(jsonPath("$.data.usage.periodEnd").value(periodEnd.toString()))
        .andExpect(jsonPath("$.data.currentPlan.code").value("PRO"))
        .andExpect(
            jsonPath("$.data.currentPlan.monthlyRequestLimit")
                .value(proPlan.getMonthlyRequestLimit()))
        .andExpect(
            jsonPath("$.data.currentPlan.price.amount").value((int) proPlan.getPriceAmount()))
        .andExpect(jsonPath("$.data.currentPlan.price.currency").value(proPlan.getPriceCurrency()))
        .andExpect(jsonPath("$.data.latestPayment.planCode").value("PRO"))
        .andExpect(jsonPath("$.data.latestPayment.amount").value((int) proPlan.getPriceAmount()))
        .andExpect(jsonPath("$.data.latestPayment.currency").value(proPlan.getPriceCurrency()))
        .andExpect(jsonPath("$.data.latestPayment.status").value("succeeded"))
        .andExpect(jsonPath("$.data.latestPayment.provider").value("vnpay"))
        .andExpect(jsonPath("$.data.latestPayment.paidAt").value(latestPaidAt.toString()));
  }
}
