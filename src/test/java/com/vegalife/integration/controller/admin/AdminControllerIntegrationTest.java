package com.vegalife.integration.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
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
class AdminControllerIntegrationTest {

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

  @Autowired private JwtTokenService jwtTokenService;

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();

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

    userRepository.save(
        User.builder()
            .username("suspendeduser")
            .email("suspended@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.suspended)
            .emailVerified(true)
            .build());

    User softDeleted =
        User.builder()
            .username("deleteduser")
            .email("deleted@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .deletedAt(Instant.now())
            .build();
    userRepository.save(softDeleted);

    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(regular);
  }

  @Test
  void listUsers_asAdmin_returns200PaginatedListWithoutSoftDeleted() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "20")
                .param("sort", "createdAt,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Users retrieved successfully"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.content.length()").value(3))
        .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].id").exists())
        .andExpect(jsonPath("$.data.content[0].email").exists())
        .andExpect(jsonPath("$.data.content[0].username").exists())
        .andExpect(jsonPath("$.data.content[0].role").exists())
        .andExpect(jsonPath("$.data.content[0].status").exists())
        .andExpect(jsonPath("$.data.content[0].createdAt").exists());
  }

  @Test
  void listUsers_filterByStatus_returnsOnlyMatchingUsers() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "suspended"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].status").value("suspended"))
        .andExpect(jsonPath("$.data.content[0].username").value("suspendeduser"));
  }

  @Test
  void listUsers_filterByRoleAndDateRange_returnsMatchingUsers() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .param("role", "ADMIN")
                .param("createdFrom", "2000-01-01T00:00:00Z")
                .param("createdTo", "2100-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].role").value("ADMIN"))
        .andExpect(jsonPath("$.data.content[0].username").value("adminuser"));
  }

  @Test
  void listUsers_withNonAdminJwt_returns403() throws Exception {
    mockMvc
        .perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void listUsers_withoutJwt_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void listUsers_withPagination_returnsSinglePageItem() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "1")
                .param("sort", "username,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.size").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(3))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].username").value("adminuser"));
  }
}
