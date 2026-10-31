package com.vegalife.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.response.auth.LoginResponse;
import com.vegalife.enums.user.AuthProvider;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.user.User;
import com.vegalife.model.user.UserIdentity;
import com.vegalife.repository.user.UserIdentityRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.shared.exception.InvalidTokenException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The BR-AUTH-022 decision tree, branch by branch. */
@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

  private static final String CODE = "google-auth-code";
  private static final String SUBJECT = "google-sub-1";
  private static final String EMAIL = "johndoe@gmail.com";

  @Mock private GoogleIdentityProvider identityProvider;
  @Mock private UserIdentityRepository userIdentityRepository;
  @Mock private UserRepository userRepository;
  @Mock private AuthService authService;

  @InjectMocks private GoogleAuthService googleAuthService;

  private User verifiedUser;
  private LoginResponse expectedResponse;

  @BeforeEach
  void setUp() {
    verifiedUser =
        User.builder()
            .id(UUID.randomUUID())
            .username("johndoe")
            .email(EMAIL)
            .emailVerified(true)
            .status(UserStatus.activated)
            .role(UserRole.USER)
            .passwordHash("$2a$12$hash")
            .build();
    expectedResponse =
        LoginResponse.builder()
            .userId(verifiedUser.getId())
            .accessToken("access")
            .refreshToken("refresh")
            .build();

    lenient()
        .when(identityProvider.resolve(CODE))
        .thenReturn(new GoogleIdentityProvider.GoogleIdentity(SUBJECT, EMAIL, true, "J"));
    lenient().when(authService.issueTokens(any(User.class))).thenReturn(expectedResponse);
  }

  @Test
  void loginWithGoogle_existingIdentity_signsInWithoutConsultingEmail() {
    when(userIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, SUBJECT))
        .thenReturn(Optional.of(UserIdentity.builder().user(verifiedUser).build()));

    LoginResponse response = googleAuthService.loginWithGoogle(CODE);

    assertThat(response).isSameAs(expectedResponse);
    verify(authService).issueTokens(verifiedUser);
    verify(userRepository, never()).findByEmail(anyString());
  }

  @Test
  void loginWithGoogle_verifiedEmailMatchesExistingAccount_autoLinksAndSignsIn() {
    when(userIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, SUBJECT))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedUser));
    when(userIdentityRepository.existsByUserIdAndProvider(
            verifiedUser.getId(), AuthProvider.GOOGLE))
        .thenReturn(false);

    LoginResponse response = googleAuthService.loginWithGoogle(CODE);

    assertThat(response.getUserId()).isEqualTo(verifiedUser.getId());
    ArgumentCaptor<UserIdentity> saved = ArgumentCaptor.forClass(UserIdentity.class);
    verify(userIdentityRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(saved.getValue().getProviderSubject()).isEqualTo(SUBJECT);
    assertThat(saved.getValue().getEmailAtProvider()).isEqualTo(EMAIL);
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void loginWithGoogle_unverifiedLocalAccount_refusesAndLinksNothing() {
    verifiedUser.setEmailVerified(false);
    verifiedUser.setStatus(UserStatus.created);
    when(userIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, SUBJECT))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedUser));

    assertThatThrownBy(() -> googleAuthService.loginWithGoogle(CODE))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Email not verified. Please verify your email before logging in.");

    verify(userIdentityRepository, never()).saveAndFlush(any(UserIdentity.class));
    verify(authService, never()).issueTokens(any());
  }

  @Test
  void loginWithGoogle_suspendedAccount_refusesAndLinksNothing() {
    verifiedUser.setStatus(UserStatus.suspended);
    when(userIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, SUBJECT))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedUser));

    assertThatThrownBy(() -> googleAuthService.loginWithGoogle(CODE))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Account is suspended");

    verify(userIdentityRepository, never()).saveAndFlush(any(UserIdentity.class));
  }

  @Test
  void loginWithGoogle_deactivatedAccount_refuses() {
    verifiedUser.setStatus(UserStatus.deactivated);
    when(userIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, SUBJECT))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedUser));

    assertThatThrownBy(() -> googleAuthService.loginWithGoogle(CODE))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Account is not active");
  }

  @Test
  void loginWithGoogle_softDeletedAccount_refusesGenerically() {
    verifiedUser.setDeletedAt(java.time.Instant.now());
    when(userIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, SUBJECT))
        .thenReturn(Optional.of(UserIdentity.builder().user(verifiedUser).build()));

    assertThatThrownBy(() -> googleAuthService.loginWithGoogle(CODE))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Invalid email/username or password");
  }

  @Test
  void loginWithGoogle_unknownEmail_createsVerifiedActivatedAccount() {
    when(userIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, SUBJECT))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    googleAuthService.loginWithGoogle(CODE);

    ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(savedUser.capture());
    User created = savedUser.getValue();
    assertThat(created.getEmail()).isEqualTo(EMAIL);
    assertThat(created.getUsername()).isEqualTo("johndoe");
    assertThat(created.getEmailVerified()).isTrue();
    assertThat(created.getStatus()).isEqualTo(UserStatus.activated);
    assertThat(created.getRole()).isEqualTo(UserRole.USER);
    assertThat(created.getPasswordHash()).isNull();
    verify(userIdentityRepository).saveAndFlush(any(UserIdentity.class));
    verify(authService).issueTokens(created);
  }

  @Test
  void loginWithGoogle_accountAlreadyHasDifferentGoogleIdentity_refuses() {
    when(userIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, SUBJECT))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedUser));
    when(userIdentityRepository.existsByUserIdAndProvider(
            verifiedUser.getId(), AuthProvider.GOOGLE))
        .thenReturn(true);

    assertThatThrownBy(() -> googleAuthService.loginWithGoogle(CODE))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("This account is already linked to a different Google account");
  }

  // --- username derivation (BR-AUTH-006) ---

  @Test
  void candidateUsername_normalisesAndKeepsValidLocalPart() {
    assertThat(GoogleAuthService.candidateUsername("John.Doe@gmail.com", 0)).isEqualTo("john.doe");
  }

  @Test
  void candidateUsername_padsTooShortLocalPartToMinimumLength() {
    assertThat(GoogleAuthService.candidateUsername("a@gmail.com", 0))
        .isEqualTo("auser")
        .hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  void candidateUsername_stripsUnsupportedCharacters() {
    assertThat(GoogleAuthService.candidateUsername("josé+tag@gmail.com", 0)).isEqualTo("jostag");
  }

  @Test
  void candidateUsername_suffixesOnRetryWithinLengthBound() {
    String localPart = "x".repeat(60) + "@gmail.com";
    String retried = GoogleAuthService.candidateUsername(localPart, 3);
    assertThat(retried).hasSizeLessThanOrEqualTo(50).endsWith("_3");
  }
}
