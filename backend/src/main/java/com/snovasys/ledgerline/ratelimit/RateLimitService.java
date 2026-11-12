package com.snovasys.ledgerline.ratelimit;

import com.snovasys.ledgerline.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decides whether a login/registration attempt may proceed.
 *  - login:    one counter per client IP  +  one counter per normalized e-mail
 *  - register: one counter per client IP
 * Counter keys contain an HMAC of the IP / e-mail, never the raw value, so Redis holds no personal data.
 * This does NOT replace the database lockout (5 failed logins -> 15 min); both run.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final RateLimitProperties properties;
    private final RateLimitStore store;          // null when rate limiting is disabled
    private final SecretKeySpec hmacKey;

    public RateLimitService(RateLimitProperties properties, Optional<RateLimitStore> store, AppProperties app) {
        this.properties = properties;
        this.store = store.orElse(null);
        if (properties.isEnabled()) {
            if (this.store == null) {
                throw new IllegalStateException("Rate limiting is enabled but no rate-limit store is configured");
            }
            properties.getLoginIp().validate("login-ip");
            properties.getLoginIdentifier().validate("login-identifier");
            properties.getRegisterIp().validate("register-ip");
            log.info("Rate limiting is ENABLED (Redis, fail-closed).");
        } else {
            log.warn("Rate limiting is DISABLED (LEDGERLINE_RATE_LIMIT_ENABLED is not true). "
                    + "This is fine for local development; production should enable it.");
        }
        // The HMAC key is derived from the JWT secret, so no extra secret has to be managed.
        this.hmacKey = new SecretKeySpec(hmac(app.jwt().secret().getBytes(StandardCharsets.UTF_8),
                "ledgerline-rate-limit-key-v1"), "HmacSHA256");
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public void checkLoginIp(String clientIp) {
        hit("login-ip", clientIp, properties.getLoginIp());
    }

    /** @param normalizedEmail the same trimmed, lower-cased value the login uses (AuthService.normalizeEmail) */
    public void checkLoginIdentifier(String normalizedEmail) {
        hit("login-id", normalizedEmail, properties.getLoginIdentifier());
    }

    public void checkRegisterIp(String clientIp) {
        hit("register-ip", clientIp, properties.getRegisterIp());
    }

    private void hit(String scope, String subject, RateLimitProperties.Rule rule) {
        if (!properties.isEnabled()) {
            return;
        }
        String key = properties.getKeyPrefix() + ":" + scope + ":" + fingerprint(subject);
        RateLimitDecision decision;
        try {
            decision = store.hit(key, rule.getLimit(), rule.getWindow());
        } catch (RateLimitException e) {
            throw e;
        } catch (RuntimeException e) {
            // Anything unexpected from the store: fail closed, never allow.
            log.warn("Rate limiter store error ({}). Failing closed.", e.getClass().getSimpleName());
            throw RateLimitException.unavailable(5);
        }
        if (!decision.allowed()) {
            log.debug("Rate limit reached for scope {}", scope);
            throw RateLimitException.exceeded(decision.retryAfterSeconds());
        }
    }

    private String fingerprint(String subject) {
        String value = subject == null ? "unknown" : subject;
        byte[] mac = hmac(hmacKey, value);
        return HexFormat.of().formatHex(mac, 0, 16);   // 128 bits is plenty and keeps keys short
    }

    private static byte[] hmac(byte[] key, String data) {
        return hmac(new SecretKeySpec(key, "HmacSHA256"), data);
    }

    private static byte[] hmac(SecretKeySpec key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
