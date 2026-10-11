package com.vegalife.integration.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

  @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

  private String adminToken;
  private String userToken;
  private java.util.UUID adminId;
  private java.util.UUID regularId;
  private java.util.UUID suspendedId;
  private java.util.UUID softDeletedId;

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
    adminId = admin.getId();

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
    regularId = regular.getId();

    User suspendedUser =
        userRepository.save(
            User.builder()
                .username("suspendeduser")
                .email("suspended@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.suspended)
                .emailVerified(true)
                .build());
    suspendedId = suspendedUser.getId();

    User softDeleted =
        userRepository.save(
            User.builder()
                .username("deleteduser")
                .email("deleted@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .deletedAt(Instant.now())
                .build());
    softDeletedId = softDeleted.getId();

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

  @Test
  void suspendUser_asAdmin_returns200AndSetsStatus() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/users/{userId}/suspend", regularId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("User suspended successfully"))
        .andExpect(jsonPath("$.data.status").value("suspended"))
        .andExpect(jsonPath("$.data.id").value(regularId.toString()));

    User updated = userRepository.findById(regularId).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updated.getStatus())
        .isEqualTo(User.Status.suspended);
  }

  @Test
  void suspendUser_alreadySuspended_returns409() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/users/{userId}/suspend", suspendedId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("User is already suspended"));
  }

  @Test
  void suspendUser_missingUser_returns404() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/users/{userId}/suspend", java.util.UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("User not found"));
  }

  @Test
  void suspendUser_softDeletedUser_returns404() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/users/{userId}/suspend", softDeletedId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("User not found"));
  }

  @Test
  void suspendUser_asNonAdmin_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/users/{userId}/suspend", regularId)
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void suspendUser_withoutJwt_returns401() throws Exception {
    mockMvc
        .perform(post("/api/admin/users/{userId}/suspend", regularId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void restoreUser_asAdmin_returns200AndSetsStatus() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/users/{userId}/restore", suspendedId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("User restored successfully"))
        .andExpect(jsonPath("$.data.status").value("activated"))
        .andExpect(jsonPath("$.data.id").value(suspendedId.toString()));

    User updated = userRepository.findById(suspendedId).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updated.getStatus())
        .isEqualTo(User.Status.activated);
  }

  @Test
  void restoreUser_notSuspended_returns409() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/users/{userId}/restore", regularId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("User is not suspended"));
  }

  @Test
  void restoreUser_missingUser_returns404() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/users/{userId}/restore", java.util.UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("User not found"));
  }

  @Test
  void restoreUser_softDeletedUser_returns404() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/users/{userId}/restore", softDeletedId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("User not found"));
  }

  @Test
  void restoreUser_asNonAdmin_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/users/{userId}/restore", suspendedId)
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void restoreUser_withoutJwt_returns401() throws Exception {
    mockMvc
        .perform(post("/api/admin/users/{userId}/restore", suspendedId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void restoreUser_thenRestoredUserCanLogin_returns200() throws Exception {
    String password = "password123";
    User suspendedUser = userRepository.findById(suspendedId).orElseThrow();
    suspendedUser.setPasswordHash(passwordEncoder.encode(password));
    userRepository.save(suspendedUser);

    String suspendedBody =
        "{\"identifier\":\"suspended@example.com\",\"password\":\"" + password + "\"}";
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(suspendedBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Account is suspended"));

    mockMvc
        .perform(
            post("/api/admin/users/{userId}/restore", suspendedId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("activated"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(suspendedBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").exists())
        .andExpect(jsonPath("$.data.refreshToken").exists())
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
  }

  @Test
  void listUsers_withSuspendedAdminToken_returns401() throws Exception {
    User admin = userRepository.findById(adminId).orElseThrow();
    admin.setStatus(User.Status.suspended);
    userRepository.save(admin);

    mockMvc
        .perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string("Account is not active"));
  }

  @Test
  void listUsers_withSoftDeletedAdminToken_returns401() throws Exception {
    User admin = userRepository.findById(adminId).orElseThrow();
    admin.setDeletedAt(Instant.now());
    userRepository.save(admin);

    mockMvc
        .perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string("Account is not active"));
  }
}
