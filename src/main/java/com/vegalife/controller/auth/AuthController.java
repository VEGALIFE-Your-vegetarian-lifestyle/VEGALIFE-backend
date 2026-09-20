package com.vegalife.controller.auth;

import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.request.auth.VerifyEmailRequest;
import com.vegalife.dto.response.auth.AuthResponse;
import com.vegalife.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@RequestParam String token) {
        AuthResponse response = userService.verifyEmail(token);
        return ResponseEntity.ok(response);
    }
}