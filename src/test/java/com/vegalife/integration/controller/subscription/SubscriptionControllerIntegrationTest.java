package com.vegalife.integration.controller.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The post-#181 subscription surface: a plan catalogue, one acquire route that claims or checks
 * out, a single-number {@code /me}, and an acquisition history.
 *
 * <p>Runs on real PostgreSQL because the claim relies on partial-index conflict inference, which H2
 * does not support.
 */
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

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private AiPlanRepository planRepository;
  @Autowired private AiSubscriptionRepository subscriptionRepository;
  @Autowired private AiUsageRepository usageRepository;
  @Autowired private PaymentLedgerRepository paymentLedgerRepository;
  @Autowired private JwtTokenService jwtTokenService;

  private User member;
  private String accessToken;
  private AiPlan freePlan;
  private AiPlan proPlan;

  @BeforeEach
  void setUp() {
    member =
        userRepository.save(
            User.builder()
                .displayName("credituser" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.USER)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
    accessToken = jwtTokenService.generateAccessToken(member);
    freePlan = planRepository.findByCode("FREE").orElseThrow();
    proPlan = planRepository.findByCode("PRO").orElseThrow();
  }

  @Test
  void getPlans_isPublicAndExposesDescriptionAndQuota() throws Exception {
    mockMvc
        .perform(get("/api/subscriptions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(
            jsonPath("$.data[?(@.code == 'FREE')].requestQuota", org.hamcrest.Matchers.hasItem(5)))
        .andExpect(
            jsonPath(
                "$.data[?(@.code == 'FREE')].description",
                org.hamcrest.Matchers.hasItem(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString()))))
        .andExpect(
            jsonPath("$.data[?(@.code == 'FREE')].price.amount", org.hamcrest.Matchers.hasItem(0)))
        .andExpect(
            jsonPath("$.data[?(@.code == 'PRO')].requestQuota", org.hamcrest.Matchers.hasItem(500)))
        // Nothing here says whether the caller already holds a plan: paid plans stack.
        .andExpect(jsonPath("$.data[0].owned").doesNotExist());
  }

  @Test
  void getMyRemainingUsage_withoutAuthentication_returns401() throws Exception {
    mockMvc
        .perform(get("/api/subscriptions/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void getMyRemainingUsage_freshAccountIsZeroUntilTheFreePlanIsClaimed() throws Exception {
    mockMvc
        .perform(get("/api/subscriptions/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.remainingUsage").value(0))
        // The contract is one number: no total, no consumed count, no window.
        .andExpect(jsonPath("$.data.totalUsage").doesNotExist())
        .andExpect(jsonPath("$.data.used").doesNotExist())
        .andExpect(jsonPath("$.data.limit").doesNotExist())
        .andExpect(jsonPath("$.data.windowStart").doesNotExist());
  }

  @Test
  void acquire_freePlan_claimsItAndGrantsTheQuota() throws Exception {
    acquire("FREE")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.planCode").value("FREE"))
        .andExpect(jsonPath("$.data.action").value("CLAIMED"))
        .andExpect(jsonPath("$.data.remainingUsage").value(5))
        .andExpect(jsonPath("$.data.paymentUrl").doesNotExist());

    assertThat(subscriptionRepository.findAllByUserId(member.getId())).hasSize(1);
    assertThat(paymentLedgerRepository.count()).isZero();

    mockMvc
        .perform(get("/api/subscriptions/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.remainingUsage").value(5));
  }

  @Test
  void acquire_freePlanTwice_returns409AndKeepsOneAcquisition() throws Exception {
    acquire("FREE").andExpect(status().isOk());

    acquire("FREE")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Plan already claimed"));

    assertThat(subscriptionRepository.findAllByUserId(member.getId())).hasSize(1);
  }

  @Test
  void acquire_inactivePlan_returns400() throws Exception {
    freePlan.setActive(false);
    planRepository.saveAndFlush(freePlan);

    acquire("FREE")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Plan is not available"));

    assertThat(subscriptionRepository.findAllByUserId(member.getId())).isEmpty();
  }

  @Test
  void acquire_unknownPlan_returns404() throws Exception {
    acquire("NOPE")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Plan not found"));
  }

  @Test
  void acquire_missingPlanCode_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/subscriptions/acquire")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void remainingUsage_subtractsLifetimeConsumption() throws Exception {
    granted(freePlan.getId(), null, Instant.now().minusSeconds(60));
    granted(proPlan.getId(), null, Instant.now().minusSeconds(60));
    usageRepository.save(AiUsage.builder().userId(member.getId()).requestCount(7).build());

    mockMvc
        .perform(get("/api/subscriptions/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.remainingUsage").value(498));
  }

  @Test
  void remainingUsage_floorsAtZeroAndNeverShowsDebt() throws Exception {
    granted(freePlan.getId(), null, Instant.now());
    usageRepository.save(AiUsage.builder().userId(member.getId()).requestCount(9).build());

    mockMvc
        .perform(get("/api/subscriptions/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.remainingUsage").value(0));
  }

  @Test
  void remainingUsage_keepsCountingADeactivatedPlansGrantedAllowance() throws Exception {
    acquire("FREE").andExpect(status().isOk());

    freePlan.setActive(false);
    planRepository.saveAndFlush(freePlan);

    mockMvc
        .perform(get("/api/subscriptions/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.remainingUsage").value(5));
  }

  @Test
  void remainingUsage_ignoresUngrantedCheckouts() throws Exception {
    PaymentLedger pending =
        paymentLedgerRepository.save(
            PaymentLedger.builder()
                .userId(member.getId())
                .amount(49000L)
                .currency("VND")
                .status(PaymentLedgerStatus.pending)
                .provider("vnpay")
                .txnRef("txn-open-" + UUID.randomUUID())
                .build());
    subscriptionRepository.save(
        AiSubscription.builder()
            .userId(member.getId())
            .planId(proPlan.getId())
            .paymentId(pending.getId())
            .grantedAt(null)
            .build());

    mockMvc
        .perform(get("/api/subscriptions/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.remainingUsage").value(0));
  }

  @Test
  void getHistory_listsClaimsAndPurchasesWithSourceAndMoneyFields() throws Exception {
    Instant now = Instant.now().minusSeconds(120);
    subscriptionRepository.save(
        AiSubscription.builder()
            .userId(member.getId())
            .planId(freePlan.getId())
            .paymentId(null)
            .grantedAt(now)
            .build());
    PaymentLedger paid =
        paymentLedgerRepository.saveAndFlush(
            PaymentLedger.builder()
                .userId(member.getId())
                .amount(49000L)
                .currency("VND")
                .status(PaymentLedgerStatus.succeeded)
                .provider("vnpay")
                .txnRef("txn-history-" + UUID.randomUUID())
                .paidAt(now)
                .build());
    subscriptionRepository.save(
        AiSubscription.builder()
            .userId(member.getId())
            .planId(proPlan.getId())
            .paymentId(paid.getId())
            .grantedAt(now)
            .build());

    mockMvc
        .perform(
            get("/api/subscriptions/me/history").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(
            jsonPath("$.data.content[*].planCode", org.hamcrest.Matchers.hasItems("FREE", "PRO")))
        .andExpect(
            jsonPath(
                "$.data.content[*].source", org.hamcrest.Matchers.hasItems("CLAIM", "PURCHASE")))
        // No lifecycle field survives: allowance never expires.
        .andExpect(jsonPath("$.data.content[0].renewalDate").doesNotExist());

    com.fasterxml.jackson.databind.JsonNode rows =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/api/subscriptions/me/history")
                            .header("Authorization", "Bearer " + accessToken))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("data")
            .get("content");

    com.fasterxml.jackson.databind.JsonNode claimRow = null;
    com.fasterxml.jackson.databind.JsonNode purchaseRow = null;
    for (com.fasterxml.jackson.databind.JsonNode row : rows) {
      if ("FREE".equals(row.get("planCode").asText())) {
        claimRow = row;
      } else {
        purchaseRow = row;
      }
    }

    assertThat(claimRow).isNotNull();
    assertThat(claimRow.get("paymentId").isNull()).isTrue();
    assertThat(claimRow.get("amount").isNull()).isTrue();
    assertThat(claimRow.get("source").asText()).isEqualTo("CLAIM");

    assertThat(purchaseRow).isNotNull();
    assertThat(purchaseRow.get("paymentId").asText()).isEqualTo(paid.getId().toString());
    assertThat(purchaseRow.get("amount").asLong()).isEqualTo(49000L);
    assertThat(purchaseRow.get("currency").asText()).isEqualTo("VND");
    assertThat(purchaseRow.get("status").asText()).isEqualTo("succeeded");
    assertThat(purchaseRow.get("source").asText()).isEqualTo("PURCHASE");
  }

  @Test
  void getHistory_withoutAuthentication_returns401() throws Exception {
    mockMvc.perform(get("/api/subscriptions/me/history")).andExpect(status().isUnauthorized());
  }

  private org.springframework.test.web.servlet.ResultActions acquire(String planCode)
      throws Exception {
    return mockMvc.perform(
        post("/api/subscriptions/acquire")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"planCode\":\"" + planCode + "\"}"));
  }

  private void granted(UUID planId, UUID paymentId, Instant grantedAt) {
    subscriptionRepository.save(
        AiSubscription.builder()
            .userId(member.getId())
            .planId(planId)
            .paymentId(paymentId)
            .grantedAt(grantedAt)
            .build());
  }
}
