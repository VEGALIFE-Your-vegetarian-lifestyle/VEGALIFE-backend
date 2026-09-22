package com.vegalife.service.auth;

import com.vegalife.dto.mapper.auth.AuthMapper;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.response.auth.AuthResponse;
import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.email.EmailService;
import com.vegalife.service.token.VerificationTokenService;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ExpiredTokenException;
import com.vegalife.shared.exception.InvalidTokenException;
import com.vegalife.shared.exception.ResourceNotFoundException;
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
  private final PasswordEncoder passwordEncoder;
  private final VerificationTokenService tokenService;
  private final EmailService emailService;

  @Value("${app.base-url:http://localhost:8080}")
  private String baseUrl;

  @Transactional
  public AuthResponse register(RegisterRequest request) {
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

    return authMapper.toAuthResponse(user);
  }

  @Transactional
  public AuthResponse verifyEmail(String token) {
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
      return authMapper.toAuthResponse(user);
    }

    user.setEmailVerified(true);
    user.setStatus(User.Status.activated);
    userRepository.save(user);

    log.info("Email verified for user: {} ({})", user.getUsername(), user.getEmail());

    return authMapper.toAuthResponse(user);
  }
}
