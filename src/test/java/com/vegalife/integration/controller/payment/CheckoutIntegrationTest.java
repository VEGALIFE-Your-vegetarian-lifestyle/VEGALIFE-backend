package com.vegalife.integration.controller.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.infrastructure.payment.vnpay.VnpayProperties;
import com.vegalife.infrastructure.payment.vnpay.VnpaySigner;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.model.user.User;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import com.vegalife.shared.config.PaymentProperties;
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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CheckoutIntegrationTest {

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

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private AiPlanRepository planRepository;

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
            .username("checkoutuser")
            .email("checkout@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    testUser = userRepository.save(testUser);

    accessToken = jwtTokenService.generateAccessToken(testUser);
  }

  @Test
  void checkout_withoutAuthentication_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/payments/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"PRO\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Unauthorized"));

    assertThat(paymentLedgerRepository.count()).isZero();
  }

  @Test
  void checkout_forProPlan_returnsSignedGatewayUrlAndPendingLedger() throws Exception {
    AiPlan proPlan = planRepository.findByCode("PRO").orElseThrow();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/payments/checkout")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"planCode\":\"PRO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Payment session created"))
            .andExpect(jsonPath("$.data.planCode").value("PRO"))
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
    assertThat(ledger.getStatus()).isEqualTo(PaymentLedger.Status.pending);
    assertThat(ledger.getUserId()).isEqualTo(testUser.getId());
    assertThat(ledger.getPlanId()).isEqualTo(proPlan.getId());
    assertThat(ledger.getProvider()).isEqualTo("vnpay");
    assertThat(ledger.getAmount()).isEqualTo(proPlan.getPriceAmount());
    assertThat(ledger.getCurrency()).isEqualTo("VND");
    assertThat(paymentLedgerRepository.count()).isEqualTo(1);
  }

  @Test
  void checkout_repeatedWithinTtl_reusesPendingRowAndTxnRef() throws Exception {
    String firstTxnRef = checkoutTxnRef();
    String secondTxnRef = checkoutTxnRef();

    assertThat(secondTxnRef).isEqualTo(firstTxnRef);
    assertThat(paymentLedgerRepository.count()).isEqualTo(1);
  }

  @Test
  void checkout_unknownPlan_returns404WithoutLedgerRow() throws Exception {
    mockMvc
        .perform(
            post("/api/payments/checkout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"NOPE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Plan not found"));

    assertThat(paymentLedgerRepository.count()).isZero();
  }

  private String checkoutTxnRef() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/payments/checkout")
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
