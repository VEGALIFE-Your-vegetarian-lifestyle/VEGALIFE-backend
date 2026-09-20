package com.vegalife.service.user;

import com.vegalife.dto.mapper.UserMapper;
import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.response.auth.AuthResponse;
import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.email.EmailService;
import com.vegalife.service.token.VerificationTokenService;
import com.vegalife.shared.config.PasswordEncoderConfig;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ExpiredTokenException;
import com.vegalife.shared.exception.InvalidTokenException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService tokenService;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = userMapper.toEntity(request, encodedPassword);
        user = userRepository.save(user);

        String token = tokenService.generateToken(user);
        String verificationLink = baseUrl + "/api/auth/verify-email?token=" + token;

        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), verificationLink);

        log.info("User registered: {} ({})", user.getUsername(), user.getEmail());

        return userMapper.toAuthResponse(user, "Verification email sent. Please check your inbox.");
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

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getEmailVerified()) {
            log.info("Email already verified for user: {}", user.getUsername());
            return userMapper.toAuthResponse(user, "Email already verified. You can now log in.");
        }

        user.setEmailVerified(true);
        user.setStatus(User.Status.activated);
        userRepository.save(user);

        log.info("Email verified for user: {} ({})", user.getUsername(), user.getEmail());

        return userMapper.toAuthResponse(user, "Email verified successfully. You can now log in.");
    }
}