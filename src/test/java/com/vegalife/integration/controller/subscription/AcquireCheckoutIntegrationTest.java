package com.vegalife.integration.controller.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.infrastructure.payment.vnpay.VnpaySigner;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.model.user.User;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.payment.PaymentProperties;
import com.vegalife.service.payment.VnpayProperties;
import com.vegalife.service.token.JwtTokenService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@code POST /api/subscriptions/acquire} for the paid branch (issue #181): one request writes the
 * ledger row and its acquisition, and returns a signed gateway redirect.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class AcquireCheckoutIntegrationTest {

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

  @Autowired private PaymentLedgerRepository paymentLedgerRepository;

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private VnpayProperties vnpayProperties;

  @Autowired private PaymentProperties paymentProperties;

  private User testUser;
  private String accessToken;

  @BeforeEach
  void setUp() {
    testUser =
        User.builder()
            .displayName("acquireuser")
            .email("acquire@example.com")
            .passwordHash("$2a$10$test")
            .role(UserRole.USER)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build();
    testUser = userRepository.save(testUser);

    accessToken = jwtTokenService.generateAccessToken(testUser);
  }

  @Test
  void acquire_withoutAuthentication_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/subscriptions/acquire")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"PRO\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Unauthorized"));

    assertThat(paymentLedgerRepository.count()).isZero();
  }

  @Test
  void acquire_forProPlan_returnsSignedGatewayUrlAndWritesBothRows() throws Exception {
    AiPlan proPlan = planRepository.findByCode("PRO").orElseThrow();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/subscriptions/acquire")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"planCode\":\"PRO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.planCode").value("PRO"))
            .andExpect(jsonPath("$.data.action").value("CHECKOUT_CREATED"))
            .andExpect(jsonPath("$.data.remainingUsage").value(0))
            .andExpect(jsonPath("$.data.status").value("pending"))
            .andExpect(jsonPath("$.data.currency").value("VND"))
            .andExpect(jsonPath("$.data.amount").value((int) proPlan.getPriceAmount()))
            .andExpect(jsonPath("$.data.txnRef").isNotEmpty())
            .andExpect(jsonPath("$.data.paymentId").isNotEmpty())
            .andExpect(jsonPath("$.data.paymentUrl").isNotEmpty())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    JsonNode data = objectMapper.readTree(body).get("data");
    String paymentId = data.get("paymentId").asText();
    String txnRef = data.get("txnRef").asText();
    String paymentUrl = data.get("paymentUrl").asText();

    // FR-018: the response carries a gateway-signed redirect, never the secret itself.
    assertThat(paymentUrl).startsWith(vnpayProperties.getPaymentUrl() + "?");
    assertThat(paymentUrl).contains("vnp_TxnRef=" + txnRef);
    assertThat(paymentUrl).contains("vnp_Amount=" + (proPlan.getPriceAmount() * 100L));
    assertThat(paymentUrl).contains("&" + VnpaySigner.SECURE_HASH_FIELD + "=");
    assertThat(body).doesNotContain(vnpayProperties.getSecureHashSecret());

    // The return URL is the configured base plus the ledger id as a path segment, so the
    // frontend result page can key its status lookup off the path rather than query params.
    String expectedReturn =
        URLEncoder.encode(
            paymentProperties.getReturnUrl() + "/" + paymentId, StandardCharsets.UTF_8);
    assertThat(paymentUrl).contains("vnp_ReturnUrl=" + expectedReturn);

    PaymentLedger ledger = paymentLedgerRepository.findByTxnRef(txnRef).orElseThrow();
    assertThat(paymentId).isEqualTo(ledger.getId().toString());
    assertThat(ledger.getStatus()).isEqualTo(PaymentLedgerStatus.pending);
    assertThat(ledger.getUserId()).isEqualTo(testUser.getId());
    assertThat(ledger.getProvider()).isEqualTo("vnpay");
    assertThat(ledger.getAmount()).isEqualTo(proPlan.getPriceAmount());
    assertThat(ledger.getCurrency()).isEqualTo("VND");
    assertThat(paymentLedgerRepository.count()).isEqualTo(1);

    // The plan moved off the ledger onto the acquisition, and it is not granted yet.
    AiSubscription acquisition =
        subscriptionRepository.findByPaymentId(ledger.getId()).orElseThrow();
    assertThat(acquisition.getPlanId()).isEqualTo(proPlan.getId());
    assertThat(acquisition.getUserId()).isEqualTo(testUser.getId());
    assertThat(acquisition.getGrantedAt()).isNull();
  }

  @Test
  void acquire_repeatedWithinTtl_reusesPendingRowsAndTxnRef() throws Exception {
    String firstTxnRef = acquireTxnRef();
    String secondTxnRef = acquireTxnRef();

    assertThat(secondTxnRef).isEqualTo(firstTxnRef);
    assertThat(paymentLedgerRepository.count()).isEqualTo(1);
    assertThat(subscriptionRepository.findAllByUserId(testUser.getId())).hasSize(1);
  }

  @Test
  void acquire_secondPurchaseOfTheSamePlan_stacksANewAcquisition() throws Exception {
    AiPlan proPlan = planRepository.findByCode("PRO").orElseThrow();
    String firstTxnRef = acquireTxnRef();

    // Mark the first checkout as paid, then wait out the reuse window by failing the payment so a
    // second acquisition is genuinely created rather than reused.
    PaymentLedger first = paymentLedgerRepository.findByTxnRef(firstTxnRef).orElseThrow();
    subscriptionRepository
        .findByPaymentId(first.getId())
        .orElseThrow()
        .setGrantedAt(java.time.Instant.now());
    first.setStatus(PaymentLedgerStatus.succeeded);
    first.setPaidAt(java.time.Instant.now());
    paymentLedgerRepository.saveAndFlush(first);

    mockMvc
        .perform(
            post("/api/subscriptions/acquire")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"PRO\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.action").value("CHECKOUT_CREATED"))
        // The paid acquisition is already counted: 500 requests bought, none consumed.
        .andExpect(jsonPath("$.data.remainingUsage").value(proPlan.getRequestQuota()));

    assertThat(subscriptionRepository.findAllByUserId(testUser.getId())).hasSize(2);
  }

  @Test
  void acquire_unknownPlan_returns404WithoutAnyRow() throws Exception {
    mockMvc
        .perform(
            post("/api/subscriptions/acquire")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"NOPE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Plan not found"));

    assertThat(paymentLedgerRepository.count()).isZero();
  }

  private String acquireTxnRef() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/subscriptions/acquire")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"planCode\":\"PRO\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("data")
        .get("txnRef")
        .asText();
  }
}
