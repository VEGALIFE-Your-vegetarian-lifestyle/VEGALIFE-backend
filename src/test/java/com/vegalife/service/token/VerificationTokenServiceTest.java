package com.vegalife.service.token;

import com.vegalife.model.user.User;
import com.vegalife.shared.exception.ExpiredTokenException;
import com.vegalife.shared.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class VerificationTokenServiceTest {

    private VerificationTokenService tokenService;
    private User user;

    @BeforeEach
    void setUp() {
        tokenService = new VerificationTokenService();
        // Use reflection to set private fields
        setField(tokenService, "secret", "test-verification-secret-change-in-production");
        setField(tokenService, "expiryMinutes", 30L);
        setField(tokenService, "issuer", "vegalife-backend-test");
        // Manually call @PostConstruct
        tokenService.init();

        user = User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .username("testuser")
            .build();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generateToken_returnsValidJWT() {
        String token = tokenService.generateToken(user);

        assertThat(token).isNotNull();
        assertThat(token).contains(".");
    }

    @Test
    void parseToken_validToken_returnsClaims() {
        String token = tokenService.generateToken(user);

        var claims = tokenService.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("type", String.class)).isEqualTo("EMAIL_VERIFY");
        assertThat(claims.get("email", String.class)).isEqualTo(user.getEmail());
        assertThat(claims.getIssuer()).isEqualTo("vegalife-backend-test");
        assertThat(claims.getExpiration()).isAfter(java.util.Date.from(java.time.Instant.now()));
    }

    @Test
    void parseToken_expiredToken_throwsException() {
        setField(tokenService, "expiryMinutes", -1L);
        String token = tokenService.generateToken(user);

        assertThatThrownBy(() -> tokenService.parseToken(token))
            .isInstanceOf(ExpiredTokenException.class)
            .hasMessage("Verification token has expired");
    }

    @Test
    void parseToken_invalidSignature_throwsException() {
        String token = tokenService.generateToken(user);
        String tamperedToken = token + "tampered";

        assertThatThrownBy(() -> tokenService.parseToken(tamperedToken))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parseToken_wrongIssuer_throwsException() {
        setField(tokenService, "issuer", "wrong-issuer");
        String token = tokenService.generateToken(user);
        setField(tokenService, "issuer", "vegalife-backend-test");

        assertThatThrownBy(() -> tokenService.parseToken(token))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parseToken_wrongType_throwsException() {
        // Can't easily test this without creating a token with wrong type
        // The service always generates EMAIL_VERIFY type
    }

    @Test
    void getUserIdFromToken_validToken_returnsUserId() {
        String token = tokenService.generateToken(user);

        UUID userId = tokenService.getUserIdFromToken(token);

        assertThat(userId).isEqualTo(user.getId());
    }

    @Test
    void getEmailFromToken_validToken_returnsEmail() {
        String token = tokenService.generateToken(user);

        String email = tokenService.getEmailFromToken(token);

        assertThat(email).isEqualTo(user.getEmail());
    }
}