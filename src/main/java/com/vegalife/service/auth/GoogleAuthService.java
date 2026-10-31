package com.vegalife.service.auth;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Google sign-in (ADR-017): resolves a verified Google identity to exactly one Vegalife account and
 * hands back the same token pair password login issues.
 *
 * <p>Resolution order is fixed by BR-AUTH-022:
 *
 * <ol>
 *   <li>a recorded identity for {@code (GOOGLE, sub)} — repeat sign-ins, email not consulted;
 *   <li>an account matching the token's email, <em>auto-linked</em> only when both sides' {@code
 *       email_verified} is true and the account is activated;
 *   <li>no match — create an account that is already verified and activated (BR-AUTH-023);
 *   <li>anything else — refuse with the message password login would give, inserting nothing.
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

  private static final String INVALID_CREDENTIALS = "Invalid email/username or password";
  private static final String SUSPENDED = "Account is suspended";
  private static final String DEACTIVATED = "Account is not active";
  private static final String EMAIL_NOT_VERIFIED =
      "Email not verified. Please verify your email before logging in.";

  private static final int USERNAME_MIN_LENGTH = 3;
  private static final int USERNAME_MAX_LENGTH = 50;
  private static final int USERNAME_ATTEMPTS = 5;
  private static final String USERNAME_PADDING = "user";

  private final GoogleIdentityProvider identityProvider;
  private final UserIdentityRepository userIdentityRepository;
  private final UserRepository userRepository;
  private final AuthService authService;

  /**
   * @param authorizationCode the code the client obtained from Google Identity Services
   * @return a login response identical in shape to {@code POST /api/auth/login}
   * @throws InvalidTokenException when the identity resolves to an account that may not sign in, or
   *     the email is not verified on either side
   */
  @Transactional
  public LoginResponse loginWithGoogle(String authorizationCode) {
    GoogleIdentityProvider.GoogleIdentity info = identityProvider.resolve(authorizationCode);

    User user = resolveAccount(info);

    log.info("User logged in with Google: {} ({})", user.getUsername(), user.getEmail());
    return authService.issueTokens(user);
  }

  /** BR-AUTH-022's four-step order. Never returns; either yields an account or throws. */
  private User resolveAccount(GoogleIdentityProvider.GoogleIdentity info) {
    Optional<UserIdentity> existing =
        userIdentityRepository.findByProviderAndProviderSubject(
            AuthProvider.GOOGLE, info.subject());
    if (existing.isPresent()) {
      User linked = existing.get().getUser();
      requireSignable(linked);
      return linked;
    }

    Optional<User> byEmail = userRepository.findByEmail(info.email());
    if (byEmail.isPresent()) {
      User matched = byEmail.get();
      requireSignable(matched);
      linkIdentity(matched, info);
      log.warn("Google identity auto-linked to existing user {}", matched.getId());
      return matched;
    }

    return createAccount(info);
  }

  /**
   * The same gate {@code AuthService.login} applies, with the same messages, so a Google sign-in
   * can never reach an account that password login would refuse. Soft-deleted accounts get the
   * generic message because they should not reveal their existence at all.
   */
  private void requireSignable(User user) {
    if (user.getDeletedAt() != null) {
      throw new InvalidTokenException(INVALID_CREDENTIALS);
    }
    if (user.getStatus() == UserStatus.suspended) {
      throw new InvalidTokenException(SUSPENDED);
    }
    if (user.getStatus() == UserStatus.deactivated) {
      throw new InvalidTokenException(DEACTIVATED);
    }
    if (!Boolean.TRUE.equals(user.getEmailVerified()) || user.getStatus() != UserStatus.activated) {
      throw new InvalidTokenException(EMAIL_NOT_VERIFIED);
    }
  }

  /**
   * Inserts the identity row for an existing account. The caller has already established that the
   * Google token carries a verified email (the verifier refuses otherwise) and that the local
   * account is verified and activated.
   *
   * <p>A concurrent sign-in that linked the same subject first surfaces as a unique-constraint
   * violation; that is the correct outcome — one subject, one account — so it is re-thrown as the
   * generic refusal rather than retried.
   */
  private void linkIdentity(User user, GoogleIdentityProvider.GoogleIdentity info) {
    if (userIdentityRepository.existsByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE)) {
      // Different Google subject for an account that already has one: refuse rather than replace.
      log.warn("Refused second Google identity for user {}", user.getId());
      throw new InvalidTokenException(
          "This account is already linked to a different Google account");
    }
    try {
      userIdentityRepository.saveAndFlush(
          UserIdentity.builder()
              .user(user)
              .provider(AuthProvider.GOOGLE)
              .providerSubject(info.subject())
              .emailAtProvider(info.email())
              .build());
    } catch (DataIntegrityViolationException e) {
      log.warn("Google identity race lost for user {}", user.getId());
      throw new InvalidTokenException(INVALID_CREDENTIALS);
    }
  }

  /** BR-AUTH-023: Google already verified the address, so the account starts usable. */
  private User createAccount(GoogleIdentityProvider.GoogleIdentity info) {
    for (int attempt = 0; attempt < USERNAME_ATTEMPTS; attempt++) {
      User user =
          User.builder()
              .username(candidateUsername(info.email(), attempt))
              .email(info.email())
              .passwordHash(null)
              .role(UserRole.USER)
              .status(UserStatus.activated)
              .emailVerified(true)
              .build();
      try {
        User saved = userRepository.saveAndFlush(user);
        userIdentityRepository.saveAndFlush(
            UserIdentity.builder()
                .user(saved)
                .provider(AuthProvider.GOOGLE)
                .providerSubject(info.subject())
                .emailAtProvider(info.email())
                .build());
        log.info("Account created through Google: {}", saved.getId());
        return saved;
      } catch (DataIntegrityViolationException e) {
        // Either the username is taken (retry with a new suffix) or a concurrent sign-in with the
        // same email won the race (then the next attempt fails on the email and we stop).
        if (attempt == USERNAME_ATTEMPTS - 1 || emailTaken(info.email())) {
          throw new InvalidTokenException(INVALID_CREDENTIALS);
        }
        log.debug("Username collision creating Google account, retrying (attempt {})", attempt + 1);
      }
    }
    throw new InvalidTokenException(INVALID_CREDENTIALS);
  }

  private boolean emailTaken(String email) {
    return userRepository.existsByEmail(email);
  }

  /**
   * Derives a BR-AUTH-006-valid username from the email local part: lowercased, stripped to {@code
   * [a-z0-9._-]}, padded when too short, truncated to leave room for a suffix, and suffixed on
   * later attempts. Uniqueness is enforced by the retry loop above, not by a pre-check.
   */
  static String candidateUsername(String email, int attempt) {
    int at = email.indexOf('@');
    String localPart = at > 0 ? email.substring(0, at) : email;
    String base = localPart.toLowerCase().replaceAll("[^a-z0-9._-]", "");
    if (base.length() < USERNAME_MIN_LENGTH) {
      base = (base + USERNAME_PADDING);
      base = base.length() > USERNAME_MAX_LENGTH ? base.substring(0, USERNAME_MAX_LENGTH) : base;
    }
    if (attempt == 0) {
      return base;
    }
    String suffix = "_" + attempt;
    int keep = Math.min(base.length(), USERNAME_MAX_LENGTH - suffix.length());
    return base.substring(0, keep) + suffix;
  }
}
