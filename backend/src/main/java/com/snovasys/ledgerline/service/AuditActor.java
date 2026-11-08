package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.domain.User;
import com.snovasys.ledgerline.security.AuthUser;
import java.util.UUID;

/** Who performed an audited action. */
public record AuditActor(UUID userId, String email, String role) {

    public static AuditActor of(AuthUser user) {
        return new AuditActor(user.id(), user.email(), user.role().name());
    }

    public static AuditActor of(User user) {
        return new AuditActor(user.getId(), user.getEmail(), user.getRole().name());
    }

    /** For events with no logged-in user, e.g. a failed login. The email is only what was typed. */
    public static AuditActor anonymous(String attemptedEmail) {
        return new AuditActor(null, attemptedEmail, null);
    }

    public static AuditActor system() {
        return new AuditActor(null, "system", "SYSTEM");
    }
}
