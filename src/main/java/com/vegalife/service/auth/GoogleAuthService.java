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

  private static final String INVALID_CREDENTIALS = "Invalid email or password";
  private static final String SUSPENDED = "Account is suspended";
  private static final String DEACTIVATED = "Account is not active";
  private static final String EMAIL_NOT_VERIFIED =
      "Email not verified. Please verify your email before logging in.";

  /** Matches {@code user.display_name VARCHAR(100)}; longer provider names are truncated. */
  private static final int DISPLAY_NAME_MAX_LENGTH = 100;

  /** Used only when Google supplies neither a name nor a usable email local part. */
  private static final String FALLBACK_DISPLAY_NAME = "Vegalife user";

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

    log.info("User logged in with Google: {} ({})", user.getDisplayName(), user.getEmail());
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

  /**
   * BR-AUTH-023: Google already verified the address, so the account starts usable. The display
   * name is seeded from Google's profile claims here and <em>only</em> here — no later sign-in
   * rewrites it (ADR-019), so a rename the user makes in Vegalife is never clobbered by Google.
   */
  private User createAccount(GoogleIdentityProvider.GoogleIdentity info) {
    User user =
        User.builder()
            .displayName(resolveDisplayName(info))
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
      // A display name is not unique, so the only constraint left to lose is the email: a
      // concurrent sign-in with the same address got there first.
      log.warn("Google account creation lost the email race: {}", e.getClass().getSimpleName());
      throw new InvalidTokenException(INVALID_CREDENTIALS);
    }
  }

  /**
   * Derives the display name for a brand-new Google account (ADR-019).
   *
   * <p>Preference order, whichever comes first and is non-blank: Google's {@code name} claim, the
   * {@code given_name} + {@code family_name} pair, the email local part, then a constant. Google
   * only guarantees these claims when the client requested the {@code profile} scope, so the
   * fallbacks are reachable in practice, not defensive padding. The result is length-capped to the
   * column, never truncated mid-surrogate-pair.
   */
  static String resolveDisplayName(GoogleIdentityProvider.GoogleIdentity info) {
    String full = trimToNull(info.name());
    if (full != null) {
      return truncate(full);
    }

    String given = trimToNull(info.givenName());
    String family = trimToNull(info.familyName());
    if (given != null && family != null) {
      return truncate(given + " " + family);
    }
    if (given != null) {
      return truncate(given);
    }
    if (family != null) {
      return truncate(family);
    }

    String email = trimToNull(info.email());
    if (email != null) {
      int at = email.indexOf('@');
      String localPart = at > 0 ? email.substring(0, at) : email;
      String candidate = trimToNull(localPart);
      if (candidate != null) {
        return truncate(candidate);
      }
    }

    return FALLBACK_DISPLAY_NAME;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** Caps at the column width without splitting a surrogate pair. */
  private static String truncate(String value) {
    if (value.length() <= DISPLAY_NAME_MAX_LENGTH) {
      return value;
    }
    int end = DISPLAY_NAME_MAX_LENGTH;
    if (Character.isHighSurrogate(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(0, end).trim();
  }
}
