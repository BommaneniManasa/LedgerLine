package com.snovasys.ledgerline.web;

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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthUser user) {
        authService.logout(user);
        return ResponseEntity.noContent().build();
    }
}
