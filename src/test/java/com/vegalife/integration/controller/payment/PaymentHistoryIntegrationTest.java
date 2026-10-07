package com.vegalife.integration.controller.payment;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.model.user.User;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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

/**
 * AC walkthrough for issue #111 across both payment-history routes. The issue's unit-test 403
 * intent is covered here instead: 401/403 come from {@code SecurityConfig}'s filter chain, so only
 * an integration test can observe them (stated deviation in the spec and PR).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class PaymentHistoryIntegrationTest {

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

  @Autowired private PaymentLedgerRepository paymentLedgerRepository;

  @Autowired private AiSubscriptionRepository aiSubscriptionRepository;

  @Autowired private AiPlanRepository aiPlanRepository;

  @Autowired private JwtTokenService jwtTokenService;

  @PersistenceContext private EntityManager entityManager;

  private String adminToken;
  private String memberToken;
  private UUID memberId;
  private UUID secondId;
  private UUID newMemberRowId;
  private UUID oldMemberRowId;
  private UUID secondRowId;
  private UUID subscriptionId;

  @BeforeEach
  void setUp() {
    paymentLedgerRepository.deleteAll();
    aiSubscriptionRepository.deleteAll();
    userRepository.deleteAll();

    User admin = saveUser("adminuser", "admin@example.com", User.Role.ADMIN);
    User member = saveUser("memberuser", "member@example.com", User.Role.USER);
    User second = saveUser("seconduser", "second@example.com", User.Role.USER);
    memberId = member.getId();
    secondId = second.getId();
    adminToken = jwtTokenService.generateAccessToken(admin);
    memberToken = jwtTokenService.generateAccessToken(member);

    AiPlan pro =
        aiPlanRepository.findByCode("PRO").orElseThrow(() -> new IllegalStateException("PRO plan"));

    AiSubscription subscription =
        aiSubscriptionRepository.save(
            AiSubscription.builder()
                .userId(memberId)
                .planId(pro.getId())
                .status(AiSubscription.Status.active)
                .startedAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .renewalDate(Instant.now().plus(27, ChronoUnit.DAYS))
                .build());
    subscriptionId = subscription.getId();

    PaymentLedger newMemberRow =
        paymentLedgerRepository.saveAndFlush(
            PaymentLedger.builder()
                .userId(memberId)
                .planId(pro.getId())
                .subscriptionId(subscriptionId)
                .amount(49000L)
                .currency("VND")
                .status(PaymentLedger.Status.succeeded)
                .provider("vnpay")
                .txnRef("txn-member-new")
                .paidAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());
    newMemberRowId = newMemberRow.getId();

    PaymentLedger oldMemberRow =
        paymentLedgerRepository.saveAndFlush(
            PaymentLedger.builder()
                .userId(memberId)
                .planId(pro.getId())
                .amount(49000L)
                .currency("VND")
                .status(PaymentLedger.Status.succeeded)
                .provider("vnpay")
                .txnRef("txn-member-old")
                .paidAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build());
    oldMemberRowId = oldMemberRow.getId();
    backdate(oldMemberRowId, Instant.now().minus(2, ChronoUnit.DAYS));

    PaymentLedger secondRow =
        paymentLedgerRepository.saveAndFlush(
            PaymentLedger.builder()
                .userId(secondId)
                .planId(pro.getId())
                .amount(49000L)
                .currency("VND")
                .status(PaymentLedger.Status.pending)
                .provider("vnpay")
                .txnRef("txn-second-pending")
                .build());
    secondRowId = secondRow.getId();
  }

  @Test
  void listMyPayments_withoutToken_returns401() throws Exception {
    mockMvc
        .perform(get("/api/payments"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Unauthorized"));
  }

  @Test
  void listPayments_withoutToken_returns401() throws Exception {
    mockMvc
        .perform(get("/api/admin/payments"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Unauthorized"));
  }

  @Test
  void listPayments_asNonAdmin_returns403() throws Exception {
    mockMvc
        .perform(get("/api/admin/payments").header("Authorization", "Bearer " + memberToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void listMyPayments_returnsOnlyOwnRowsNewestFirstWithoutUserIdField() throws Exception {
    mockMvc
        .perform(get("/api/payments").header("Authorization", "Bearer " + memberToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Payment history retrieved successfully"))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.content[0].id").value(newMemberRowId.toString()))
        .andExpect(jsonPath("$.data.content[1].id").value(oldMemberRowId.toString()))
        .andExpect(jsonPath("$.data.content[0].userId").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].plan.code").value("PRO"))
        .andExpect(jsonPath("$.data.content[0].subscription.id").value(subscriptionId.toString()))
        .andExpect(jsonPath("$.data.content[0].subscription.status").value("active"));
  }

  @Test
  void listPayments_asAdmin_returnsAllRowsWithUserIdOnEach() throws Exception {
    mockMvc
        .perform(get("/api/admin/payments").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.content.length()").value(3))
        .andExpect(
            jsonPath(
                "$.data.content[*].userId", hasItems(memberId.toString(), secondId.toString())))
        .andExpect(jsonPath("$.data.content[0].id").value(secondRowId.toString()));
  }

  @Test
  void listPayments_withUserIdFilter_narrowsToThatUser() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .param("userId", secondId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(secondRowId.toString()))
        .andExpect(jsonPath("$.data.content[0].userId").value(secondId.toString()));
  }

  @Test
  void listPayments_withStatusFilter_narrowsToStatus() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "pending"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(secondRowId.toString()));
  }

  @Test
  void listPayments_withDateRange_excludesRowsCreatedBeforeRange() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .param(
                    "createdFrom",
                    DateTimeFormatter.ISO_INSTANT.format(Instant.now().minus(1, ChronoUnit.DAYS))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(
            jsonPath(
                "$.data.content[*].id",
                hasItems(newMemberRowId.toString(), secondRowId.toString())));
  }

  @Test
  void listMyPayments_beyondLastPage_returns200WithEmptyContent() throws Exception {
    mockMvc
        .perform(
            get("/api/payments")
                .header("Authorization", "Bearer " + memberToken)
                .param("page", "5")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content.length()").value(0))
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void listPayments_withMalformedUserId_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .param("userId", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void listPayments_withInvalidStatus_returns400WithValidationMessage() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "bogus"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void listPayments_withCreatedFromAfterCreatedTo_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2100-01-01T00:00:00Z")
                .param("createdTo", "2000-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("createdFrom must be before createdTo"));
  }

  private User saveUser(String username, String email, User.Role role) {
    return userRepository.save(
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(role)
            .status(User.Status.activated)
            .emailVerified(true)
            .build());
  }

  private void backdate(UUID ledgerId, Instant when) {
    paymentLedgerRepository.flush();
    entityManager
        .createNativeQuery("UPDATE payment_ledger SET created_at = :ts WHERE id = :id")
        .setParameter("ts", Timestamp.from(when))
        .setParameter("id", ledgerId)
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();
  }
}
