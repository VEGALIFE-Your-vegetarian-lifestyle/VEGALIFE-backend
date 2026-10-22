package com.vegalife.integration.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.user.User;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class AdminPlanControllerIntegrationTest {

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

  @Autowired private UserRepository userRepository;

  @Autowired private AiPlanRepository aiPlanRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    User admin =
        userRepository.save(
            User.builder()
                .username("adminuser")
                .email("admin@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.ADMIN)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    User regular =
        userRepository.save(
            User.builder()
                .username("regularuser")
                .email("user@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(regular);
  }

  @Test
  void listPlans_asAdmin_returns200IncludingInactive() throws Exception {
    aiPlanRepository.save(
        AiPlan.builder()
            .code("LEGACY")
            .name("Legacy")
            .monthlyRequestLimit(5)
            .priceAmount(0)
            .priceCurrency("VND")
            .active(false)
            .sortOrder(99)
            .build());

    mockMvc
        .perform(get("/api/admin/plans").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void listPlans_asMember_returns403() throws Exception {
    mockMvc
        .perform(get("/api/admin/plans").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void listPlans_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/plans")).andExpect(status().isUnauthorized());
  }

  @Test
  void createPlan_asAdmin_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/plans")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PREMIUM",
                      "name": "Premium",
                      "monthlyRequestLimit": 500,
                      "priceAmount": 99000,
                      "priceCurrency": "VND",
                      "active": true,
                      "sortOrder": 3
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.code").value("PREMIUM"))
        .andExpect(jsonPath("$.data.active").value(true));
  }

  @Test
  void createPlan_duplicateCode_returns409() throws Exception {
    aiPlanRepository.save(
        AiPlan.builder()
            .code("PREMIUM")
            .name("Premium")
            .monthlyRequestLimit(500)
            .priceAmount(99000)
            .priceCurrency("VND")
            .active(true)
            .sortOrder(3)
            .build());

    mockMvc
        .perform(
            post("/api/admin/plans")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "premium",
                      "name": "Premium Again",
                      "monthlyRequestLimit": 500,
                      "priceAmount": 99000,
                      "priceCurrency": "VND",
                      "active": true,
                      "sortOrder": 4
                    }
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void createPlan_asMember_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/plans")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "PREMIUM",
                      "name": "Premium",
                      "monthlyRequestLimit": 500,
                      "priceAmount": 99000,
                      "priceCurrency": "VND",
                      "active": true,
                      "sortOrder": 3
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void updatePlan_asAdmin_changesOnlySuppliedFields() throws Exception {
    AiPlan plan =
        aiPlanRepository.save(
            AiPlan.builder()
                .code("PREMIUM")
                .name("Premium")
                .monthlyRequestLimit(500)
                .priceAmount(99000)
                .priceCurrency("VND")
                .active(true)
                .sortOrder(3)
                .build());

    mockMvc
        .perform(
            patch("/api/admin/plans/{id}", plan.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceAmount\": 79000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.priceAmount").value(79000))
        .andExpect(jsonPath("$.data.name").value("Premium"));
  }

  @Test
  void updatePlan_unknownId_returns404() throws Exception {
    mockMvc
        .perform(
            patch("/api/admin/plans/{id}", java.util.UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sortOrder\": 1}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updatePlan_deactivatingFreePlan_returns409() throws Exception {
    AiPlan free =
        aiPlanRepository
            .findByCode("FREE")
            .orElseGet(
                () ->
                    aiPlanRepository.save(
                        AiPlan.builder()
                            .code("FREE")
                            .name("Free")
                            .monthlyRequestLimit(20)
                            .priceAmount(0)
                            .priceCurrency("VND")
                            .active(true)
                            .sortOrder(1)
                            .build()));

    mockMvc
        .perform(
            patch("/api/admin/plans/{id}", free.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}"))
        .andExpect(status().isConflict());
  }

  @Test
  void deletePlan_asAdmin_deactivates() throws Exception {
    AiPlan plan =
        aiPlanRepository.save(
            AiPlan.builder()
                .code("PREMIUM")
                .name("Premium")
                .monthlyRequestLimit(500)
                .priceAmount(99000)
                .priceCurrency("VND")
                .active(true)
                .sortOrder(3)
                .build());

    mockMvc
        .perform(
            delete("/api/admin/plans/{id}", plan.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    assertThat(aiPlanRepository.findById(plan.getId()).orElseThrow().isActive()).isFalse();
  }

  @Test
  void deletePlan_freePlan_returns409() throws Exception {
    AiPlan free =
        aiPlanRepository
            .findByCode("FREE")
            .orElseGet(
                () ->
                    aiPlanRepository.save(
                        AiPlan.builder()
                            .code("FREE")
                            .name("Free")
                            .monthlyRequestLimit(20)
                            .priceAmount(0)
                            .priceCurrency("VND")
                            .active(true)
                            .sortOrder(1)
                            .build()));

    mockMvc
        .perform(
            delete("/api/admin/plans/{id}", free.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isConflict());
  }

  @Test
  void deletePlan_unknownId_returns404() throws Exception {
    mockMvc
        .perform(
            delete("/api/admin/plans/{id}", java.util.UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void deletePlan_asMember_returns403() throws Exception {
    AiPlan plan =
        aiPlanRepository.save(
            AiPlan.builder()
                .code("PREMIUM")
                .name("Premium")
                .monthlyRequestLimit(500)
                .priceAmount(99000)
                .priceCurrency("VND")
                .active(true)
                .sortOrder(3)
                .build());

    mockMvc
        .perform(
            delete("/api/admin/plans/{id}", plan.getId())
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }
}
