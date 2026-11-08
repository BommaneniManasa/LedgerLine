package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.domain.AuditAction;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.domain.User;
import com.snovasys.ledgerline.exception.BadRequestException;
import com.snovasys.ledgerline.exception.NotFoundException;
import com.snovasys.ledgerline.repository.UserRepository;
import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.web.dto.UserDtos.ChangePasswordRequest;
import com.snovasys.ledgerline.web.dto.UserDtos.UpdateProfileRequest;
import com.snovasys.ledgerline.web.dto.UserDtos.UserView;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile of the logged-in user. The user is always taken from the token, never from a request field. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserView getProfile(AuthUser caller) {
        return UserView.from(load(caller));
    }

    @Transactional
    public UserView updateProfile(AuthUser caller, UpdateProfileRequest request) {
        User user = load(caller);
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        auditService.record(AuditAction.PROFILE_UPDATED, AuditOutcome.SUCCESS, AuditActor.of(caller),
                "USER", caller.id().toString(), null);
        return UserView.from(user);
    }

    /** Changing the password also invalidates every token issued before now (all other sessions). */
    @Transactional
    public void changePassword(AuthUser caller, ChangePasswordRequest request) {
        User user = load(caller);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.recordIndependent(AuditAction.PASSWORD_CHANGED, AuditOutcome.FAILURE,
                    AuditActor.of(caller), "USER", caller.id().toString(), Map.of("reason", "wrong_current_password"));
            throw new BadRequestException("WRONG_PASSWORD", "Current password is incorrect");
        }
        PasswordPolicy.validate(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTokenValidAfter(Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.SECONDS));
        auditService.record(AuditAction.PASSWORD_CHANGED, AuditOutcome.SUCCESS, AuditActor.of(caller),
                "USER", caller.id().toString(), null);
    }

    private User load(AuthUser caller) {
        return userRepository.findById(caller.id()).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
