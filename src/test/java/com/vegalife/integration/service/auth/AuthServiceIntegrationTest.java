package com.vegalife.integration.service.auth;

import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.response.auth.AuthResponse;
import com.vegalife.model.user.User;
import com.vegalife.model.user.User.Role;
import com.vegalife.model.user.User.Status;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.auth.AuthService;
import com.vegalife.service.email.EmailService;
import com.vegalife.service.token.VerificationTokenService;
import com.vegalife.shared.exception.DuplicateResourceException;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthServiceIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
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

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenService tokenService;

    @MockBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void register_savesUserToDatabase() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("dbuser");
        request.setEmail("dbuser@test.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("dbuser");
        assertThat(response.getEmail()).isEqualTo("dbuser@test.com");

        User savedUser = userRepository.findByEmail("dbuser@test.com").orElseThrow();
        assertThat(savedUser.getUsername()).isEqualTo("dbuser");
        assertThat(savedUser.getEmail()).isEqualTo("dbuser@test.com");
        assertThat(savedUser.getPasswordHash()).isNotNull();
        assertThat(savedUser.getEmailVerified()).isFalse();
        assertThat(savedUser.getStatus()).isEqualTo(Status.created);
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void register_duplicateEmail_throwsException() {
        RegisterRequest request1 = new RegisterRequest();
        request1.setUsername("user1");
        request1.setEmail("same@test.com");
        request1.setPassword("password123");
        request1.setConfirmPassword("password123");
        authService.register(request1);

        RegisterRequest request2 = new RegisterRequest();
        request2.setUsername("user2");
        request2.setEmail("same@test.com");
        request2.setPassword("password123");
        request2.setConfirmPassword("password123");

        assertThatThrownBy(() -> authService.register(request2))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessage("Email already registered");
    }

    @Test
    void register_duplicateUsername_throwsException() {
        RegisterRequest request1 = new RegisterRequest();
        request1.setUsername("sameuser");
        request1.setEmail("user1@test.com");
        request1.setPassword("password123");
        request1.setConfirmPassword("password123");
        authService.register(request1);

        RegisterRequest request2 = new RegisterRequest();
        request2.setUsername("sameuser");
        request2.setEmail("user2@test.com");
        request2.setPassword("password123");
        request2.setConfirmPassword("password123");

        assertThatThrownBy(() -> authService.register(request2))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessage("Username already taken");
    }

    @Test
    void verifyEmail_activatesUser() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("verifyuser");
        request.setEmail("verify@test.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        AuthResponse registerResponse = authService.register(request);

        User user = userRepository.findByEmail("verify@test.com").orElseThrow();
        String token = tokenService.generateToken(user);

        AuthResponse verifyResponse = authService.verifyEmail(token);

        assertThat(verifyResponse).isNotNull();

        User verifiedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(verifiedUser.getEmailVerified()).isTrue();
        assertThat(verifiedUser.getStatus()).isEqualTo(Status.activated);
    }

    @Test
    void verifyEmail_alreadyVerified_returnsSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("verifyuser2");
        request.setEmail("verify2@test.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        AuthResponse registerResponse = authService.register(request);

        User user = userRepository.findByEmail("verify2@test.com").orElseThrow();
        String token = tokenService.generateToken(user);

        authService.verifyEmail(token);

        AuthResponse verifyResponse = authService.verifyEmail(token);
        assertThat(verifyResponse).isNotNull();

        User verifiedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(verifiedUser.getEmailVerified()).isTrue();
        assertThat(verifiedUser.getStatus()).isEqualTo(Status.activated);
    }
}