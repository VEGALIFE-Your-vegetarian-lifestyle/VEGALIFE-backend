package com.vegalife.unit.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserAccountAuthState;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import com.vegalife.shared.exception.AccountInactiveException;
import com.vegalife.shared.security.JwtAuthenticationProvider;
import com.vegalife.shared.security.JwtAuthenticationToken;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationProviderTest {

  @Mock private JwtTokenService jwtTokenService;

  @Mock private UserRepository userRepository;

  @InjectMocks private JwtAuthenticationProvider provider;

  private UUID userId;
  private Claims claims;
  private UserAccountAuthState activatedAccount;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    claims = mock(Claims.class);
    lenient().when(claims.getSubject()).thenReturn(userId.toString());

    activatedAccount =
        new UserAccountAuthState() {
          @Override
          public UUID getId() {
            return userId;
          }

          @Override
          public User.Status getStatus() {
            return User.Status.activated;
          }

          @Override
          public User.Role getRole() {
            return User.Role.USER;
          }

          @Override
          public Instant getDeletedAt() {
            return null;
          }
        };
  }

  @Test
  void authenticate_activatedAccount_returnsAuthenticatedTokenWithRoleAuthority() {
    JwtAuthenticationToken request = new JwtAuthenticationToken("raw.token");
    when(jwtTokenService.parseAccessToken("raw.token")).thenReturn(claims);
    when(userRepository.findAccountAuthStateById(userId)).thenReturn(Optional.of(activatedAccount));

    Authentication result = provider.authenticate(request);

    assertThat(result).isInstanceOf(JwtAuthenticationToken.class);
    JwtAuthenticationToken authenticated = (JwtAuthenticationToken) result;
    assertThat(authenticated.isAuthenticated()).isTrue();
    assertThat(authenticated.getUserId()).isEqualTo(userId);
    assertThat(authenticated.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_USER");
  }

  @Test
  void authenticate_adminRole_returnsRoleAdminAuthority() {
    UserAccountAuthState adminAccount =
        new UserAccountAuthState() {
          @Override
          public UUID getId() {
            return userId;
          }

          @Override
          public User.Status getStatus() {
            return User.Status.activated;
          }

          @Override
          public User.Role getRole() {
            return User.Role.ADMIN;
          }

          @Override
          public Instant getDeletedAt() {
            return null;
          }
        };
    JwtAuthenticationToken request = new JwtAuthenticationToken("raw.token");
    when(jwtTokenService.parseAccessToken("raw.token")).thenReturn(claims);
    when(userRepository.findAccountAuthStateById(userId)).thenReturn(Optional.of(adminAccount));

    Authentication result = provider.authenticate(request);

    assertThat(result.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN");
  }

  @Test
  void authenticate_suspendedAccount_throwsAccountInactive() {
    UserAccountAuthState suspended =
        new UserAccountAuthState() {
          @Override
          public UUID getId() {
            return userId;
          }

          @Override
          public User.Status getStatus() {
            return User.Status.suspended;
          }

          @Override
          public User.Role getRole() {
            return User.Role.USER;
          }

          @Override
          public Instant getDeletedAt() {
            return null;
          }
        };
    JwtAuthenticationToken request = new JwtAuthenticationToken("raw.token");
    when(jwtTokenService.parseAccessToken("raw.token")).thenReturn(claims);
    when(userRepository.findAccountAuthStateById(userId)).thenReturn(Optional.of(suspended));

    assertThatThrownBy(() -> provider.authenticate(request))
        .isInstanceOf(AccountInactiveException.class)
        .hasMessage("Account is not active");
  }

  @Test
  void authenticate_deactivatedAccount_throwsAccountInactive() {
    UserAccountAuthState deactivated =
        new UserAccountAuthState() {
          @Override
          public UUID getId() {
            return userId;
          }

          @Override
          public User.Status getStatus() {
            return User.Status.deactivated;
          }

          @Override
          public User.Role getRole() {
            return User.Role.USER;
          }

          @Override
          public Instant getDeletedAt() {
            return null;
          }
        };
    JwtAuthenticationToken request = new JwtAuthenticationToken("raw.token");
    when(jwtTokenService.parseAccessToken("raw.token")).thenReturn(claims);
    when(userRepository.findAccountAuthStateById(userId)).thenReturn(Optional.of(deactivated));

    assertThatThrownBy(() -> provider.authenticate(request))
        .isInstanceOf(AccountInactiveException.class)
        .hasMessage("Account is not active");
  }

  @Test
  void authenticate_softDeletedAccount_throwsAccountInactive() {
    UserAccountAuthState softDeleted =
        new UserAccountAuthState() {
          @Override
          public UUID getId() {
            return userId;
          }

          @Override
          public User.Status getStatus() {
            return User.Status.activated;
          }

          @Override
          public User.Role getRole() {
            return User.Role.USER;
          }

          @Override
          public Instant getDeletedAt() {
            return Instant.now();
          }
        };
    JwtAuthenticationToken request = new JwtAuthenticationToken("raw.token");
    when(jwtTokenService.parseAccessToken("raw.token")).thenReturn(claims);
    when(userRepository.findAccountAuthStateById(userId)).thenReturn(Optional.of(softDeleted));

    assertThatThrownBy(() -> provider.authenticate(request))
        .isInstanceOf(AccountInactiveException.class)
        .hasMessage("Account is not active");
  }

  @Test
  void authenticate_missingAccount_throwsAccountInactive() {
    JwtAuthenticationToken request = new JwtAuthenticationToken("raw.token");
    when(jwtTokenService.parseAccessToken("raw.token")).thenReturn(claims);
    when(userRepository.findAccountAuthStateById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> provider.authenticate(request))
        .isInstanceOf(AccountInactiveException.class)
        .hasMessage("Account is not active");
  }

  @Test
  void authenticate_invalidSubject_throwsAuthenticationServiceException() {
    when(claims.getSubject()).thenReturn("not-a-uuid");
    JwtAuthenticationToken request = new JwtAuthenticationToken("raw.token");
    when(jwtTokenService.parseAccessToken("raw.token")).thenReturn(claims);

    assertThatThrownBy(() -> provider.authenticate(request))
        .isInstanceOf(AuthenticationServiceException.class)
        .hasMessage("Invalid access token");
  }

  @Test
  void authenticate_nonJwtAuthentication_returnsNull() {
    assertThat(provider.authenticate(mock(Authentication.class))).isNull();
  }

  @Test
  void supports_jwtAuthenticationToken_returnsTrue() {
    assertThat(provider.supports(JwtAuthenticationToken.class)).isTrue();
  }

  @Test
  void supports_otherAuthentication_returnsFalse() {
    assertThat(provider.supports(Authentication.class)).isFalse();
  }

  @Test
  void authenticate_activatedAccount_authoritiesContainSingleRole() {
    JwtAuthenticationToken request = new JwtAuthenticationToken("raw.token");
    when(jwtTokenService.parseAccessToken("raw.token")).thenReturn(claims);
    when(userRepository.findAccountAuthStateById(userId)).thenReturn(Optional.of(activatedAccount));

    Authentication result = provider.authenticate(request);

    assertThat(result.getAuthorities()).hasSize(1);
  }
}
