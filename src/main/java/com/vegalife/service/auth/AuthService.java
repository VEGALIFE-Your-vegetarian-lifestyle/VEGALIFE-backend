package com.vegalife.service.auth;

import com.vegalife.dto.mapper.auth.AuthMapper;
import com.vegalife.dto.mapper.auth.LoginMapper;
import com.vegalife.dto.request.auth.LoginRequest;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.response.auth.LoginResponse;
import com.vegalife.dto.response.auth.RegisterResponse;
import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.email.EmailService;
import com.vegalife.service.token.JwtTokenService;
import com.vegalife.service.token.VerificationTokenService;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ExpiredTokenException;
import com.vegalife.shared.exception.InvalidTokenException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
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

  private final UserRepository userRepository;
  private final AuthMapper authMapper;
  private final LoginMapper loginMapper;
  private final PasswordEncoder passwordEncoder;
  private final VerificationTokenService tokenService;
  private final JwtTokenService jwtTokenService;
  private final EmailService emailService;

  @Value("${app.base-url:http://localhost:8080}")
  private String baseUrl;

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

    String token = tokenService.generateToken(user);
    String verificationLink = baseUrl + "/api/auth/verify-email?token=" + token;

    emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), verificationLink);

    log.info("User registered: {} ({})", user.getUsername(), user.getEmail());

    return authMapper.toRegisterResponse(user);
  }

  @Transactional
  public RegisterResponse verifyEmail(String token) {
    UUID userId;
    try {
      userId = tokenService.getUserIdFromToken(token);
    } catch (ExpiredTokenException e) {
      throw new ExpiredTokenException("Verification link has expired. Please register again.");
    } catch (InvalidTokenException e) {
      throw new InvalidTokenException("Invalid verification link.");
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (user.getEmailVerified()) {
      log.info("Email already verified for user: {}", user.getUsername());
      return authMapper.toRegisterResponse(user);
    }

    user.setEmailVerified(true);
    user.setStatus(User.Status.activated);
    userRepository.save(user);

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
}
