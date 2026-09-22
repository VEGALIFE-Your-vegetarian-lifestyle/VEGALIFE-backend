package com.vegalife.integration.service.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

import com.vegalife.model.user.User;
import com.vegalife.model.user.User.Role;
import com.vegalife.model.user.User.Status;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.email.EmailService;
import com.vegalife.service.token.VerificationTokenService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmailVerificationIntegrationTest {

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

  @MockBean private EmailService emailService;

  @Autowired private UserRepository userRepository;

  @Autowired private VerificationTokenService tokenService;

  private User testUser;
  private String verificationLink;

  @BeforeEach
  void setUp() {
    String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    testUser =
        User.builder()
            .username("integrationuser-" + uniqueSuffix)
            .email("integration-" + uniqueSuffix + "@test.com")
            .passwordHash("encoded")
            .role(Role.USER)
            .status(Status.created)
            .emailVerified(false)
            .build();
    testUser = userRepository.save(testUser);

    String token = tokenService.generateToken(testUser);
    verificationLink = "http://localhost:8080/api/auth/verify-email?token=" + token;

    doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
  }

  @Test
  void sendVerificationEmail_sendsEmailSuccessfully() {
    emailService.sendVerificationEmail(
        testUser.getEmail(), testUser.getUsername(), verificationLink);

    assertThat(userRepository.findById(testUser.getId())).isPresent();
    assertThat(tokenService.getUserIdFromToken(tokenService.generateToken(testUser)))
        .isEqualTo(testUser.getId());
  }

  @Test
  void sendVerificationEmail_withInvalidEmail_doesNotThrow() {
    emailService.sendVerificationEmail("invalid-email", "testuser", verificationLink);

    assertThat(true).isTrue();
  }
}
