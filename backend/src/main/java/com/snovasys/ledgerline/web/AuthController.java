package com.snovasys.ledgerline.web;

import com.snovasys.ledgerline.ratelimit.RateLimitService;
import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.service.AuthService;
import com.snovasys.ledgerline.web.dto.AuthDtos.LoginRequest;
import com.snovasys.ledgerline.web.dto.AuthDtos.LoginResponse;
import com.snovasys.ledgerline.web.dto.AuthDtos.RegisterRequest;
import com.snovasys.ledgerline.web.dto.UserDtos.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    public AuthController(AuthService authService, RateLimitService rateLimitService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        // Per-e-mail rate limit (the per-IP limit already ran in RateLimitFilter). Applies equally to
        // unknown and existing e-mails, so a 429 reveals nothing about which accounts exist.
        rateLimitService.checkLoginIdentifier(AuthService.normalizeEmail(request.email()));
        return authService.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthUser user) {
        authService.logout(user);
        return ResponseEntity.noContent().build();
    }
}
