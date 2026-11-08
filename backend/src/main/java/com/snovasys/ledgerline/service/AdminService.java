package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.domain.Account;
import com.snovasys.ledgerline.domain.AuditAction;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.domain.Role;
import com.snovasys.ledgerline.domain.User;
import com.snovasys.ledgerline.domain.UserStatus;
import com.snovasys.ledgerline.exception.BusinessRuleException;
import com.snovasys.ledgerline.exception.ConflictException;
import com.snovasys.ledgerline.exception.NotFoundException;
import com.snovasys.ledgerline.repository.AccountRepository;
import com.snovasys.ledgerline.repository.UserRepository;
import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.web.dto.AccountDtos.AccountAdminView;
import com.snovasys.ledgerline.web.dto.AdminDtos.CreateUserRequest;
import com.snovasys.ledgerline.web.dto.PageResponse;
import com.snovasys.ledgerline.web.dto.UserDtos.UserView;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User and account management for ADMINs. Every change is audited with who, what, before and after. */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminService(UserRepository userRepository, AccountRepository accountRepository,
                        AccountService accountService, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> listUsers(int page, int size) {
        return PageResponse.of(userRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), UserView::from);
    }

    @Transactional
    public UserView createUser(AuthUser admin, CreateUserRequest request) {
        String email = AuthService.normalizeEmail(request.email());
        PasswordPolicy.validate(request.password());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), null, request.role());
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        auditService.record(AuditAction.USER_CREATED_BY_ADMIN, AuditOutcome.SUCCESS, AuditActor.of(admin),
                "USER", user.getId().toString(), Map.of("role", user.getRole().name()));
        return UserView.from(user);
    }

    /** Suspend / disable / re-activate. An admin cannot change their own status (no accidental lock-out). */
    @Transactional
    public UserView setUserStatus(AuthUser admin, UUID userId, UserStatus status) {
        rejectSelf(admin, userId);
        User user = userRepository.findLockedById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        UserStatus old = user.getStatus();
        user.setStatus(status);
        if (status != UserStatus.ACTIVE) {
            // kill every token that was issued before now
            user.setTokenValidAfter(Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.SECONDS));
        }
        auditService.record(AuditAction.USER_STATUS_CHANGED, AuditOutcome.SUCCESS, AuditActor.of(admin),
                "USER", userId.toString(), Map.of("from", old.name(), "to", status.name()));
        return UserView.from(user);
    }

    /** Role changes are the most sensitive admin action (privilege escalation), so they have extra guards. */
    @Transactional
    public UserView setUserRole(AuthUser admin, UUID userId, Role role) {
        rejectSelf(admin, userId);
        User user = userRepository.findLockedById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Role old = user.getRole();
        if (old != role && accountRepository.existsByUserId(userId)) {
            throw new ConflictException("USER_HAS_ACCOUNTS", "Cannot change the role of a user who owns accounts");
        }
        user.setRole(role);
        user.setTokenValidAfter(Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.SECONDS));
        auditService.record(AuditAction.USER_ROLE_CHANGED, AuditOutcome.SUCCESS, AuditActor.of(admin),
                "USER", userId.toString(), Map.of("from", old.name(), "to", role.name()));
        return UserView.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountAdminView> listAccounts(int page, int size) {
        return PageResponse.of(accountRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), AccountAdminView::from);
    }

    @Transactional
    public AccountAdminView createAccountForUser(AuthUser admin, UUID userId, String currency) {
        Account account = accountService.createAccount(userId, currency, AuditActor.of(admin));
        return AccountAdminView.from(account);
    }

    private static void rejectSelf(AuthUser admin, UUID targetUserId) {
        if (admin.id().equals(targetUserId)) {
            throw new BusinessRuleException("SELF_CHANGE_NOT_ALLOWED", "You cannot change your own role or status");
        }
    }
}
