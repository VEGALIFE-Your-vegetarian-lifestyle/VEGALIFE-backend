package com.vegalife.integration.controller.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.infrastructure.payment.PaymentGateway;
import com.vegalife.infrastructure.payment.vnpay.VnpayProperties;
import com.vegalife.infrastructure.payment.vnpay.VnpaySigner;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.model.user.User;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end coverage of {@code GET /api/payments/{paymentId}} (issue #106): authenticated access
 * only, ownership-scoped 404s, and a read that never reaches the gateway (ADR-008, BR-PAY-001).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class PaymentStatusIntegrationTest {

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

  @Autowired private PaymentLedgerRepository paymentLedgerRepository;

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private VnpayProperties vnpayProperties;

  @MockBean private PaymentGateway paymentGateway;

  private User testUser;
  private String accessToken;
  private AiPlan proPlan;

  @BeforeEach
  void setUp() {
    testUser =
        User.builder()
            .username("statususer")
            .email("status@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    testUser = userRepository.save(testUser);

    accessToken = jwtTokenService.generateAccessToken(testUser);
    proPlan = planRepository.findByCode("PRO").orElseThrow();
  }

  @Test
  void getStatus_withoutAuthentication_returns401() throws Exception {
    mockMvc
        .perform(get("/api/payments/{paymentId}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Unauthorized"));
  }

  @Test
  void getStatus_unknownPaymentId_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/payments/{paymentId}", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Payment not found"));
  }

  @Test
  void getStatus_malformedPaymentId_returns404Not500() throws Exception {
    mockMvc
        .perform(
            get("/api/payments/{paymentId}", "not-a-uuid")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Payment not found"));
  }

  @Test
  void getStatus_foreignPayment_returns404IndistinguishableFromUnknown() throws Exception {
    User otherUser =
        userRepository.save(
            User.builder()
                .username("otherstatususer")
                .email("other-status@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    PaymentLedger foreignLedger = pendingLedger(otherUser.getId());

    String foreignBody =
        mockMvc
            .perform(
                get("/api/payments/{paymentId}", foreignLedger.getId())
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String unknownBody =
        mockMvc
            .perform(
                get("/api/payments/{paymentId}", UUID.randomUUID())
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // FR-008/NFR-SEC-001: another member's id must be indistinguishable from a nonexistent one.
    assertThat(foreignBody).isEqualTo(unknownBody);
    assertThat(foreignBody).doesNotContain(foreignLedger.getId().toString());
  }

  @Test
  void getStatus_ownedPendingPayment_returnsLedgerPayloadWithoutGatewayCall() throws Exception {
    PaymentLedger ledger = pendingLedger(testUser.getId());
    clearInvocations(paymentGateway);

    mockMvc
        .perform(
            get("/api/payments/{paymentId}", ledger.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Payment retrieved successfully"))
        .andExpect(jsonPath("$.data.paymentId").value(ledger.getId().toString()))
        .andExpect(jsonPath("$.data.status").value("pending"))
        .andExpect(jsonPath("$.data.amount").value((int) proPlan.getPriceAmount()))
        .andExpect(jsonPath("$.data.currency").value("VND"))
        .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.data.paidAt").value(nullValue()))
        .andExpect(jsonPath("$.data.plan.code").value("PRO"))
        .andExpect(jsonPath("$.data.plan.name").value(proPlan.getName()))
        .andExpect(
            jsonPath("$.data.plan.monthlyRequestLimit").value(proPlan.getMonthlyRequestLimit()))
        .andExpect(jsonPath("$.data.plan.price.amount").value((int) proPlan.getPriceAmount()))
        .andExpect(jsonPath("$.data.plan.price.currency").value("VND"));

    // FR-007/ADR-008: a status read is a ledger lookup, never a gateway round-trip.
    verifyNoInteractions(paymentGateway);

    // NFR-SEC-002: reading changes nothing — the row is still the untouched pending one.
    PaymentLedger reloaded = paymentLedgerRepository.findById(ledger.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PaymentLedger.Status.pending);
    assertThat(reloaded.getPaidAt()).isNull();
    assertThat(reloaded.getResponseCode()).isNull();
  }

  @Test
  void getStatus_afterIpnFulfilment_returnsFinalStatusAndPaidAt() throws Exception {
    PaymentLedger ledger = pendingLedger(testUser.getId());
    // The gateway bean is mocked in this class, so the checksum is accepted without the real
    // HMAC; everything downstream (amount check, fulfilment) runs for real.
    when(paymentGateway.verifyCallback(anyMap())).thenReturn(true);

    mockMvc
        .perform(post("/api/payments/vnpay/ipn").params(signedIpn(ledger)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("00"));

    mockMvc
        .perform(
            get("/api/payments/{paymentId}", ledger.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paymentId").value(ledger.getId().toString()))
        .andExpect(jsonPath("$.data.status").value("succeeded"))
        .andExpect(jsonPath("$.data.amount").value((int) proPlan.getPriceAmount()))
        .andExpect(jsonPath("$.data.currency").value("VND"))
        .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.data.paidAt").isNotEmpty())
        .andExpect(jsonPath("$.data.plan.code").value("PRO"));
  }

  private PaymentLedger pendingLedger(UUID userId) {
    PaymentLedger ledger =
        paymentLedgerRepository.save(
            PaymentLedger.builder()
                .userId(userId)
                .planId(proPlan.getId())
                .amount(proPlan.getPriceAmount())
                .currency(proPlan.getPriceCurrency())
                .status(PaymentLedger.Status.pending)
                .provider("vnpay")
                .build());
    ledger.setTxnRef(ledger.getId().toString().replace("-", ""));
    return paymentLedgerRepository.save(ledger);
  }

  /** Signs the same notification shape VNPay sends, using the configured (fake) test secret. */
  private MultiValueMap<String, String> signedIpn(PaymentLedger ledger) {
    VnpaySigner signer = new VnpaySigner(vnpayProperties.getSecureHashSecret());
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_TxnRef", ledger.getTxnRef());
    params.put("vnp_Amount", String.valueOf(ledger.getAmount() * 100L));
    params.put("vnp_ResponseCode", "00");
    params.put("vnp_TransactionStatus", "00");
    params.put("vnp_TransactionNo", "14093211");
    params.put("vnp_BankCode", "NCB");
    params.put("vnp_OrderInfo", "Upgrade to PRO");
    params.put(VnpaySigner.SECURE_HASH_FIELD, signer.signParams(params));

    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    params.forEach(query::add);
    return query;
  }
}
