package com.vegalife.integration.controller.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.infrastructure.payment.vnpay.VnpayProperties;
import com.vegalife.infrastructure.payment.vnpay.VnpaySigner;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.model.user.User;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
class VnpayIpnIntegrationTest {

  private static final String IPN_PATH = "/api/payments/vnpay/ipn";

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

  @Autowired private PaymentLedgerRepository paymentLedgerRepository;

  @Autowired private OutboundMessageRepository outboundMessageRepository;

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private VnpayProperties vnpayProperties;

  private User testUser;
  private AiPlan proPlan;
  private VnpaySigner signer;

  @BeforeEach
  void setUp() {
    testUser =
        User.builder()
            .username("ipnuser")
            .email("ipn@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    testUser = userRepository.save(testUser);

    proPlan = planRepository.findByCode("PRO").orElseThrow();
    signer = new VnpaySigner(vnpayProperties.getSecureHashSecret());
  }

  @Test
  void ipn_success_acknowledgesAndFulfilsSubscriptionAndReceipt() throws Exception {
    PaymentLedger ledger = pendingLedger();

    Instant beforeFulfil = Instant.now();
    postIpn(signedIpn(ledger.getTxnRef(), ledger.getAmount(), "00", "00"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.RspCode").value("00"))
        .andExpect(jsonPath("$.Message").value("Confirm Success"))
        .andExpect(jsonPath("$.success").doesNotExist());

    PaymentLedger reloaded = paymentLedgerRepository.findByTxnRef(ledger.getTxnRef()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PaymentLedger.Status.succeeded);
    assertThat(reloaded.getPaidAt()).isNotNull();
    assertThat(reloaded.getPaidAt()).isAfter(beforeFulfil);
    assertThat(reloaded.getProvider()).isEqualTo("vnpay");
    assertThat(reloaded.getProviderReference()).isEqualTo("14093211");
    assertThat(reloaded.getResponseCode()).isEqualTo("00");
    assertThat(reloaded.getBankCode()).isEqualTo("NCB");

    // BR-PAY-007: the same notification that flips the ledger also upgrades the account.
    AiSubscription subscription =
        subscriptionRepository.findByUserId(testUser.getId()).orElseThrow();
    assertThat(subscription.getPlanId()).isEqualTo(proPlan.getId());
    assertThat(subscription.getStatus()).isEqualTo(AiSubscription.Status.active);
    // BR-PAY-008: renewal is paid_at plus one calendar month in UTC.
    Instant expectedRenewal = reloaded.getPaidAt().atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
    assertThat(subscription.getRenewalDate())
        .isCloseTo(expectedRenewal, within(Duration.ofSeconds(1)));

    // Receipt queued in the same transaction (scheduling is off, so the row survives).
    List<OutboundMessage> receipts = receipts();
    assertThat(receipts).hasSize(1);
    OutboundMessage receipt = receipts.get(0);
    assertThat(receipt.getStatus()).isEqualTo(OutboundStatus.PENDING);
    assertThat(receipt.getExpiresAt()).isNull();
    assertThat(receipt.getPayload()).contains("\"type\":\"RECEIPT\"");
    assertThat(receipt.getPayload()).contains(ledger.getTxnRef());
    assertThat(receipt.getPayload()).contains("\"amount\":" + proPlan.getPriceAmount());
  }

  @Test
  void ipn_replayedNotification_acksAgainWithoutSecondFulfilment() throws Exception {
    PaymentLedger ledger = pendingLedger();
    Map<String, String> params = signedIpn(ledger.getTxnRef(), ledger.getAmount(), "00", "00");

    postIpn(params).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
    Instant firstPaidAt =
        paymentLedgerRepository.findByTxnRef(ledger.getTxnRef()).orElseThrow().getPaidAt();

    postIpn(params)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("00"))
        .andExpect(jsonPath("$.Message").value("Confirm Success"));

    PaymentLedger reloaded = paymentLedgerRepository.findByTxnRef(ledger.getTxnRef()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PaymentLedger.Status.succeeded);
    assertThat(reloaded.getPaidAt()).isEqualTo(firstPaidAt);
    assertThat(subscriptionRepository.findByUserId(testUser.getId())).isPresent();
    assertThat(receipts()).hasSize(1);
  }

  @Test
  void ipn_checksumMismatch_rejectsWith97AndNoSideEffects() throws Exception {
    PaymentLedger ledger = pendingLedger();
    Map<String, String> params = signedIpn(ledger.getTxnRef(), ledger.getAmount(), "00", "00");
    params.put(VnpaySigner.SECURE_HASH_FIELD, "0".repeat(128));

    postIpn(params)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("97"))
        .andExpect(jsonPath("$.Message").value("Invalid Checksum"));

    PaymentLedger reloaded = paymentLedgerRepository.findByTxnRef(ledger.getTxnRef()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PaymentLedger.Status.pending);
    assertThat(subscriptionRepository.findByUserId(testUser.getId())).isEmpty();
    assertThat(receipts()).isEmpty();
  }

  @Test
  void ipn_unknownTransactionReference_acksOrderNotFound() throws Exception {
    String unknownTxnRef = "unknown" + UUID.randomUUID().toString().replace("-", "");

    postIpn(signedIpn(unknownTxnRef, proPlan.getPriceAmount(), "00", "00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("01"))
        .andExpect(jsonPath("$.Message").value("Order not Found"));
  }

  @Test
  void ipn_amountMismatch_acksInvalidAmountAndLeavesLedgerPending() throws Exception {
    PaymentLedger ledger = pendingLedger();

    postIpn(signedIpn(ledger.getTxnRef(), ledger.getAmount() + 1, "00", "00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("04"))
        .andExpect(jsonPath("$.Message").value("Invalid Amount"));

    PaymentLedger reloaded = paymentLedgerRepository.findByTxnRef(ledger.getTxnRef()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PaymentLedger.Status.pending);
    assertThat(subscriptionRepository.findByUserId(testUser.getId())).isEmpty();
    assertThat(receipts()).isEmpty();
  }

  @Test
  void ipn_getWithProcessingStatus_acksAndKeepsLedgerPending() throws Exception {
    PaymentLedger ledger = pendingLedger();

    MockHttpServletRequestBuilder request = get(IPN_PATH);
    signedIpn(ledger.getTxnRef(), ledger.getAmount(), "00", "01").forEach(request::param);
    mockMvc
        .perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("00"))
        .andExpect(jsonPath("$.Message").value("Confirm Success"));

    PaymentLedger reloaded = paymentLedgerRepository.findByTxnRef(ledger.getTxnRef()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PaymentLedger.Status.pending);
    assertThat(reloaded.getPaidAt()).isNull();
    assertThat(subscriptionRepository.findByUserId(testUser.getId())).isEmpty();
    assertThat(receipts()).isEmpty();
  }

  @Test
  void ipn_declinedPayment_acksAndRecordsFailureWithoutFulfilment() throws Exception {
    PaymentLedger ledger = pendingLedger();

    postIpn(signedIpn(ledger.getTxnRef(), ledger.getAmount(), "24", "00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("00"))
        .andExpect(jsonPath("$.Message").value("Confirm Success"));

    PaymentLedger reloaded = paymentLedgerRepository.findByTxnRef(ledger.getTxnRef()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PaymentLedger.Status.failed);
    assertThat(reloaded.getResponseCode()).isEqualTo("24");
    assertThat(reloaded.getBankCode()).isEqualTo("NCB");
    assertThat(reloaded.getPaidAt()).isNull();
    assertThat(subscriptionRepository.findByUserId(testUser.getId())).isEmpty();
    assertThat(receipts()).isEmpty();
  }

  private PaymentLedger pendingLedger() {
    PaymentLedger ledger =
        paymentLedgerRepository.save(
            PaymentLedger.builder()
                .userId(testUser.getId())
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
  private Map<String, String> signedIpn(
      String txnRef, long amountVnd, String responseCode, String transactionStatus) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_TxnRef", txnRef);
    params.put("vnp_Amount", String.valueOf(amountVnd * 100L));
    params.put("vnp_ResponseCode", responseCode);
    params.put("vnp_TransactionStatus", transactionStatus);
    params.put("vnp_TransactionNo", "14093211");
    params.put("vnp_BankCode", "NCB");
    params.put("vnp_OrderInfo", "Upgrade to PRO");
    params.put(VnpaySigner.SECURE_HASH_FIELD, signer.signParams(params));
    return params;
  }

  private ResultActions postIpn(Map<String, String> params) throws Exception {
    MockHttpServletRequestBuilder request = post(IPN_PATH);
    params.forEach(request::param);
    return mockMvc.perform(request);
  }

  private List<OutboundMessage> receipts() {
    return outboundMessageRepository.findAll().stream()
        .filter(message -> testUser.getEmail().equals(message.getRecipient()))
        .toList();
  }
}
