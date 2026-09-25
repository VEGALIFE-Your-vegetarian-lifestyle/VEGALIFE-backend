package com.vegalife.service.auth;

import com.vegalife.dto.mapper.auth.AuthMapper;
import com.vegalife.dto.mapper.auth.LoginMapper;
import com.vegalife.dto.request.auth.ForgotPasswordRequest;
import com.vegalife.dto.request.auth.LoginRequest;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.request.auth.ResetPasswordRequest;
import com.vegalife.dto.request.auth.VerifyEmailRequest;
import com.vegalife.dto.response.auth.LoginResponse;
import com.vegalife.dto.response.auth.RegisterResponse;
import com.vegalife.model.token.OtpCode;
import com.vegalife.model.token.OtpPurpose;
import com.vegalife.model.user.User;
import com.vegalife.repository.token.OtpCodeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.email.EmailService;
import com.vegalife.service.token.JwtTokenService;
import com.vegalife.service.token.VerificationTokenService;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ExpiredTokenException;
import com.vegalife.shared.exception.InvalidTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private static final String INVALID_RESET_CODE_MESSAGE =
      "Invalid or already used password reset code";
  private static final String EXPIRED_RESET_CODE_MESSAGE =
      "Password reset code has expired. Please request a new one.";
  private static final String INVALID_VERIFICATION_CODE_MESSAGE =
      "Invalid or already used verification code";
  private static final String EXPIRED_VERIFICATION_CODE_MESSAGE =
      "Verification code has expired. Please request a new one.";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final AuthMapper authMapper;
  private final LoginMapper loginMapper;
  private final PasswordEncoder passwordEncoder;
  private final VerificationTokenService tokenService;
  private final JwtTokenService jwtTokenService;
  private final EmailService emailService;
  private final OtpCodeRepository otpCodeRepository;

  @Value("${app.base-url:http://localhost:8080}")
  private String baseUrl;

  @Value("${app.password-reset.otp-expiry-minutes:10}")
  private int otpExpiryMinutes;

  @Value("${app.email-verification.otp-expiry-minutes:10}")
  private int emailVerificationOtpExpiryMinutes;

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateResourceException("Email already registered");
    }

    if (userRepository.existsByUsername(request.getUsername())) {
      throw new DuplicateResourceException("Username already taken");
    }

    String encodedPassword = passwordEncoder.encode(request.getPassword());

    User user = authMapper.toEntity(request, encodedPassword);
    user = userRepository.save(user);

    otpCodeRepository.markAllUnusedByUserIdAndPurpose(
        user.getId(), OtpPurpose.EMAIL_VERIFICATION, Instant.now());

    String otp = generateOtp();
    otpCodeRepository.save(
        OtpCode.builder()
            .user(user)
            .otpHash(sha256(otp))
            .purpose(OtpPurpose.EMAIL_VERIFICATION)
            .expiresAt(Instant.now().plusSeconds(emailVerificationOtpExpiryMinutes * 60L))
            .build());

    emailService.sendVerificationOtp(user.getEmail(), user.getUsername(), otp);

    log.info("User registered: {} ({})", user.getUsername(), user.getEmail());

    return authMapper.toRegisterResponse(user);
  }

  @Transactional
  public RegisterResponse verifyEmail(VerifyEmailRequest request) {
    User user =
        userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new InvalidTokenException(INVALID_VERIFICATION_CODE_MESSAGE));

    if (user.getEmailVerified()) {
      log.info("Email already verified for user: {}", user.getUsername());
      return authMapper.toRegisterResponse(user);
    }

    OtpCode otpRow =
        otpCodeRepository
            .findLatestUnusedByUserIdAndPurpose(user.getId(), OtpPurpose.EMAIL_VERIFICATION)
            .orElseThrow(() -> new InvalidTokenException(INVALID_VERIFICATION_CODE_MESSAGE));

    if (otpRow.isExpired()) {
      throw new ExpiredTokenException(EXPIRED_VERIFICATION_CODE_MESSAGE);
    }

    if (!otpRow.getOtpHash().equals(sha256(request.getOtp()))) {
      throw new InvalidTokenException(INVALID_VERIFICATION_CODE_MESSAGE);
    }

    user.setEmailVerified(true);
    user.setStatus(User.Status.activated);
    userRepository.save(user);

    otpRow.setUsedAt(Instant.now());
    otpCodeRepository.save(otpRow);

    log.info("Email verified for user: {} ({})", user.getUsername(), user.getEmail());

    return authMapper.toRegisterResponse(user);
  }

  @Transactional
  public LoginResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.getIdentifier())
            .or(() -> userRepository.findByUsername(request.getIdentifier()))
            .orElseThrow(() -> new InvalidTokenException("Invalid email/username or password"));

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new InvalidTokenException("Invalid email/username or password");
    }

    if (user.getStatus() == User.Status.suspended) {
      throw new InvalidTokenException("Account is suspended");
    }

    if (user.getStatus() == User.Status.deactivated) {
      throw new InvalidTokenException("Account is not active");
    }

    if (!Boolean.TRUE.equals(user.getEmailVerified())
        || user.getStatus() != User.Status.activated) {
      throw new InvalidTokenException(
          "Email not verified. Please verify your email before logging in.");
    }

    String accessToken = jwtTokenService.generateAccessToken(user);
    String refreshToken = jwtTokenService.generateRefreshToken(user);

    user.setLastLoginAt(Instant.now());
    userRepository.save(user);

    log.info("User logged in: {} ({})", user.getUsername(), user.getEmail());

    return loginMapper.toLoginResponse(user).toBuilder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .expiresIn(jwtTokenService.getAccessTokenExpirySeconds())
        .build();
  }

  @Transactional
  public LoginResponse refreshToken(String refreshToken) {
    com.vegalife.model.token.RefreshToken storedToken =
        jwtTokenService.validateRefreshToken(refreshToken);
    User user = storedToken.getUser();

    if (user.getDeletedAt() != null || user.getStatus() != User.Status.activated) {
      throw new InvalidTokenException("Account is not active");
    }

    String newAccessToken = jwtTokenService.generateAccessToken(user);

    log.info("Access token refreshed for user: {}", user.getUsername());

    return loginMapper.toLoginResponse(user).toBuilder()
        .accessToken(newAccessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .expiresIn(jwtTokenService.getAccessTokenExpirySeconds())
        .build();
  }

  @Transactional
  public void logout(String accessToken) {
    jwtTokenService.blacklistAccessToken(
        jwtTokenService.extractJti(accessToken),
        jwtTokenService.extractIssuer(accessToken),
        jwtTokenService.extractExpiration(accessToken));

    jwtTokenService.revokeAllUserRefreshTokens(
        jwtTokenService.validateAccessTokenAndGetUserId(accessToken));

    log.info("User logged out");
  }

  @Transactional
  public void forgotPassword(ForgotPasswordRequest request) {
    Optional<User> userOpt = userRepository.findByEmail(request.getEmail());

    if (userOpt.isEmpty()) {
      log.debug("Password reset requested for unknown email");
      return;
    }

    User user = userOpt.get();

    otpCodeRepository.markAllUnusedByUserIdAndPurpose(
        user.getId(), OtpPurpose.PASSWORD_RESET, Instant.now());

    String otp = generateOtp();
    otpCodeRepository.save(
        OtpCode.builder()
            .user(user)
            .otpHash(sha256(otp))
            .purpose(OtpPurpose.PASSWORD_RESET)
            .expiresAt(Instant.now().plusSeconds(otpExpiryMinutes * 60L))
            .build());

    emailService.sendPasswordResetOtp(user.getEmail(), user.getUsername(), otp);

    log.info("Password reset OTP issued for user: {}", user.getUsername());
  }

  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    User user =
        userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new InvalidTokenException(INVALID_RESET_CODE_MESSAGE));

    OtpCode otpRow =
        otpCodeRepository
            .findLatestUnusedByUserIdAndPurpose(user.getId(), OtpPurpose.PASSWORD_RESET)
            .orElseThrow(() -> new InvalidTokenException(INVALID_RESET_CODE_MESSAGE));

    if (otpRow.isExpired()) {
      throw new ExpiredTokenException(EXPIRED_RESET_CODE_MESSAGE);
    }

    if (!otpRow.getOtpHash().equals(sha256(request.getOtp()))) {
      throw new InvalidTokenException(INVALID_RESET_CODE_MESSAGE);
    }

    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);

    otpRow.setUsedAt(Instant.now());
    otpCodeRepository.save(otpRow);

    jwtTokenService.revokeAllUserRefreshTokens(user.getId());

    log.info("Password reset completed for user: {}", user.getUsername());
  }

  private String generateOtp() {
    return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new InvalidTokenException("SHA-256 algorithm not available");
    }
  }
}
