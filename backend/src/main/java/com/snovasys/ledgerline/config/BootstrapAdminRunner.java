package com.snovasys.ledgerline.config;

import com.snovasys.ledgerline.domain.AuditAction;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.domain.Role;
import com.snovasys.ledgerline.domain.User;
import com.snovasys.ledgerline.repository.UserRepository;
import com.snovasys.ledgerline.service.AuditActor;
import com.snovasys.ledgerline.service.AuditService;
import com.snovasys.ledgerline.service.AuthService;
import com.snovasys.ledgerline.service.PasswordPolicy;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the very first ADMIN from environment variables (there is no default admin and no default password).
 * Skipped when the variables are empty or the user already exists. The password is never logged.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public BootstrapAdminRunner(AppProperties properties, UserRepository userRepository,
                                PasswordEncoder passwordEncoder, AuditService auditService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = AuthService.normalizeEmail(properties.bootstrap().adminEmail());
        String password = properties.bootstrap().adminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("No bootstrap admin configured (LEDGERLINE_BOOTSTRAP_ADMIN_EMAIL / _PASSWORD not set).");
            return;
        }
        if (userRepository.existsByEmail(email)) {
            log.info("Bootstrap admin already exists, nothing to do.");
            return;
        }
        PasswordPolicy.validate(password);
        User admin = userRepository.save(new User(email, passwordEncoder.encode(password), "System Administrator",
                null, Role.ADMIN));
        auditService.record(AuditAction.ADMIN_BOOTSTRAPPED, AuditOutcome.SUCCESS, AuditActor.system(),
                "USER", admin.getId().toString(), Map.of("email", email));
        log.info("Bootstrap admin created.");
    }
}
