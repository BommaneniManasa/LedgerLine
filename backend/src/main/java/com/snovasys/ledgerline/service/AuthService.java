package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.domain.AuditAction;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.domain.RevokedToken;
import com.snovasys.ledgerline.domain.Role;
import com.snovasys.ledgerline.domain.User;
import com.snovasys.ledgerline.domain.UserStatus;
import com.snovasys.ledgerline.exception.ConflictException;
import com.snovasys.ledgerline.exception.UnauthorizedException;
import com.snovasys.ledgerline.repository.RevokedTokenRepository;
import com.snovasys.ledgerline.repository.UserRepository;
import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.security.JwtService;
import com.snovasys.ledgerline.web.dto.AuthDtos.LoginRequest;
import com.snovasys.ledgerline.web.dto.AuthDtos.LoginResponse;
import com.snovasys.ledgerline.web.dto.AuthDtos.RegisterRequest;
import com.snovasys.ledgerline.web.dto.UserDtos.UserView;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    /** Used to spend the same time on unknown emails as on real ones (prevents user enumeration by timing). */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, RevokedTokenRepository revokedTokenRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptService loginAttemptService, AuditService auditService) {
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-" + System.nanoTime());
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Self-registration. Always creates a CUSTOMER: the caller cannot choose a role. */
    @Transactional
    public UserView register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        PasswordPolicy.validate(request.password());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), blankToNull(request.phone()), Role.CUSTOMER);
        try {
            user = userRepository.saveAndFlush(user);   // the UNIQUE index catches two simultaneous sign-ups
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        auditService.record(AuditAction.USER_REGISTERED, AuditOutcome.SUCCESS, AuditActor.of(user),
                "USER", user.getId().toString(), Map.of("role", Role.CUSTOMER.name()));
        return UserView.from(user);
    }

    /**
     * Not @Transactional on purpose: the attempt counter and the audit event are saved in their own
     * transactions, so they survive even when this method throws.
     */
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            auditService.recordIndependent(AuditAction.LOGIN_FAILURE, AuditOutcome.FAILURE,
                    AuditActor.anonymous(email), "USER", null, Map.of("reason", "unknown_email"));
            throw new UnauthorizedException("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }

        if (user.isLocked(Instant.now())) {
            auditService.recordIndependent(AuditAction.LOGIN_FAILURE, AuditOutcome.FAILURE,
                    AuditActor.of(user), "USER", user.getId().toString(), Map.of("reason", "account_locked"));
            throw new UnauthorizedException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed attempts. Try again later.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean locked = loginAttemptService.recordFailure(user.getId());
            auditService.recordIndependent(AuditAction.LOGIN_FAILURE, AuditOutcome.FAILURE,
                    AuditActor.of(user), "USER", user.getId().toString(),
                    Map.of("reason", "bad_password", "lockedNow", String.valueOf(locked)));
            throw new UnauthorizedException("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.recordIndependent(AuditAction.LOGIN_FAILURE, AuditOutcome.FAILURE,
                    AuditActor.of(user), "USER", user.getId().toString(),
                    Map.of("reason", "account_" + user.getStatus().name().toLowerCase(Locale.ROOT)));
            throw new UnauthorizedException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE",
                    "This user account is not active");
        }

        loginAttemptService.recordSuccess(user.getId());
        JwtService.IssuedToken token = jwtService.issue(user);
        auditService.recordIndependent(AuditAction.LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                AuditActor.of(user), "USER", user.getId().toString(), null);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), UserView.from(user));
    }

    /** Logout = remember the token id so this exact token can never be used again. */
    @Transactional
    public void logout(AuthUser user) {
        if (!revokedTokenRepository.existsById(user.tokenId())) {
            revokedTokenRepository.save(new RevokedToken(user.tokenId(), user.id(), user.tokenExpiresAt()));
        }
        auditService.record(AuditAction.LOGOUT, AuditOutcome.SUCCESS, AuditActor.of(user),
                "USER", user.id().toString(), null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
