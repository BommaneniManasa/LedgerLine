package com.snovasys.ledgerline.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.snovasys.ledgerline.ratelimit.HttpTestSupport.Resp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Per-e-mail login limit (3 / 30 s) with a high per-IP limit, so only the e-mail counter can trigger.
 * Same prerequisites as RateLimitIntegrationTest.
 */
@EnabledIfEnvironmentVariable(named = "LEDGERLINE_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LEDGERLINE_BOOTSTRAP_ADMIN_EMAIL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LEDGERLINE_TEST_REDIS_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ledgerline.rate-limit.enabled=true",
        "ledgerline.rate-limit.redis-url=${LEDGERLINE_TEST_REDIS_URL}",
        "ledgerline.rate-limit.key-prefix=ll:rl:test:${random.uuid}",
        "ledgerline.rate-limit.login-ip.limit=1000",
        "ledgerline.rate-limit.login-identifier.limit=3",
        "ledgerline.rate-limit.login-identifier.window=PT30S"
})
class RateLimitIdentifierIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void theSameEmailIsLimitedWhateverTheCaseAndOtherEmailsAreNot() throws Exception {
        String base = "idl-" + UUID.randomUUID().toString().substring(0, 8) + "@test.io";
        assertEquals(401, HttpTestSupport.post(port, "/api/auth/login", HttpTestSupport.loginJson(base)).status());
        assertEquals(401, HttpTestSupport.post(port, "/api/auth/login",
                HttpTestSupport.loginJson(base.toUpperCase())).status());
        assertEquals(401, HttpTestSupport.post(port, "/api/auth/login",
                HttpTestSupport.loginJson("  " + base + "  ")).status());
        // 4th attempt for the same (normalized) e-mail
        Resp blocked = HttpTestSupport.post(port, "/api/auth/login", HttpTestSupport.loginJson(base));
        assertEquals(429, blocked.status(), blocked.body());
        assertNotNull(blocked.header("Retry-After"));
        // a different e-mail from the same IP is unaffected
        String other = "idl-" + UUID.randomUUID().toString().substring(0, 8) + "@test.io";
        assertEquals(401, HttpTestSupport.post(port, "/api/auth/login", HttpTestSupport.loginJson(other)).status());
    }
}
