package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.config.AppProperties;
import com.snovasys.ledgerline.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brute-force protection. Each method runs in its OWN transaction, so the failed-attempt counter
 * is saved even though the login request itself ends with an error.
 */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AppProperties properties;

    public LoginAttemptService(UserRepository userRepository, AppProperties properties) {
        this.userRepository = userRepository;
        this.properties = properties;
    }

    /** @return true if this failure locked the account */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(UUID userId) {
        return userRepository.findLockedById(userId).map(user -> {
            int failures = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failures);
            if (failures >= properties.security().maxFailedLogins()) {
                user.setLockedUntil(Instant.now().plus(properties.security().lockDuration()));
                user.setFailedLoginCount(0);
                return true;
            }
            return false;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        userRepository.findLockedById(userId).ifPresent(user -> {
            if (user.getFailedLoginCount() != 0 || user.getLockedUntil() != null) {
                user.setFailedLoginCount(0);
                user.setLockedUntil(null);
            }
        });
    }
}
