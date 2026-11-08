package com.snovasys.ledgerline.security;

import com.snovasys.ledgerline.domain.User;
import com.snovasys.ledgerline.domain.UserStatus;
import com.snovasys.ledgerline.repository.RevokedTokenRepository;
import com.snovasys.ledgerline.repository.UserRepository;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Runs after the JWT signature/expiry has been verified. It re-checks everything that can change
 * after a token was issued, using the database as the source of truth:
 * the user still exists, is ACTIVE, did not log out (jti), and did not change their password since.
 * The role used for authorization comes from the user row, never from the token.
 */
@Component
public class DbJwtAuthenticationConverter implements Converter<Jwt, AuthUserToken> {

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public DbJwtAuthenticationConverter(UserRepository userRepository, RevokedTokenRepository revokedTokenRepository) {
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    public AuthUserToken convert(Jwt jwt) {
        UUID userId = parseUserId(jwt.getSubject());
        String jti = jwt.getId();
        if (jti == null || revokedTokenRepository.existsById(jti)) {
            throw invalid();
        }
        User user = userRepository.findById(userId).orElseThrow(DbJwtAuthenticationConverter::invalid);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalid();
        }
        if (jwt.getIssuedAt() == null
                || jwt.getIssuedAt().isBefore(user.getTokenValidAfter().truncatedTo(ChronoUnit.SECONDS))) {
            throw invalid();
        }
        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole(), jti, jwt.getExpiresAt());
        return new AuthUserToken(principal, jwt);
    }

    private static UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    private static InvalidBearerTokenException invalid() {
        return new InvalidBearerTokenException("Invalid or expired token");
    }
}
