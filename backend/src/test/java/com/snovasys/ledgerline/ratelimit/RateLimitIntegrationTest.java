package com.snovasys.ledgerline.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.snovasys.ledgerline.ratelimit.HttpTestSupport.Resp;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Real HTTP + real PostgreSQL + real Redis, rate limiting ON.
 * Skipped unless the same variables as LedgerlineIntegrationTest are set PLUS LEDGERLINE_TEST_REDIS_URL.
 * Use an EMPTY scratch database and a throw-away Redis. Keys use a random prefix.
 * Limits here: login per IP 4 / 30 s, login per e-mail 3 / 30 s, register per IP 2 / 30 s.
 */
@EnabledIfEnvironmentVariable(named = "LEDGERLINE_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LEDGERLINE_BOOTSTRAP_ADMIN_EMAIL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LEDGERLINE_TEST_REDIS_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ledgerline.rate-limit.enabled=true",
        "ledgerline.rate-limit.redis-url=${LEDGERLINE_TEST_REDIS_URL}",
        "ledgerline.rate-limit.key-prefix=ll:rl:test:${random.uuid}",
        "ledgerline.rate-limit.login-ip.limit=4",
        "ledgerline.rate-limit.login-ip.window=PT30S",
        "ledgerline.rate-limit.login-identifier.limit=3",
        "ledgerline.rate-limit.login-identifier.window=PT30S",
        "ledgerline.rate-limit.register-ip.limit=2",
        "ledgerline.rate-limit.register-ip.window=PT30S"
})
class RateLimitIntegrationTest {

    @Value("${local.server.port}")
    int port;

    private static String email() {
        return "rl-" + UUID.randomUUID().toString().substring(0, 8) + "@test.io";
    }

    @Test
    void loginIsLimitedPerIpWith429AndRetryAfterAndRegistrationIsSeparate() throws Exception {
        // Order matters inside this one test because the IP counters are shared by everything on 127.0.0.1.
        for (int i = 0; i < 4; i++) {
            Resp r = HttpTestSupport.post(port, "/api/auth/login", HttpTestSupport.loginJson(email()));
            assertEquals(401, r.status(), "attempt " + (i + 1) + " is within the limit: " + r.body());
        }
        Resp blocked = HttpTestSupport.post(port, "/api/auth/login", HttpTestSupport.loginJson(email()));
        assertEquals(429, blocked.status(), blocked.body());
        assertEquals("RATE_LIMITED", blocked.field("code"));
        String retryAfter = blocked.header("Retry-After");
        assertNotNull(retryAfter);
        assertTrue(Long.parseLong(retryAfter) >= 1 && Long.parseLong(retryAfter) <= 30, retryAfter);
        assertFalse(blocked.body().toLowerCase().contains("redis"), "no internals in the error body");

        // registration has its own budget (2), unaffected by the exhausted login budget
        String pw = "Str0ngPassw0rd!";
        for (int i = 0; i < 2; i++) {
            Resp reg = HttpTestSupport.post(port, "/api/auth/register",
                    "{\"email\":\"" + email() + "\",\"password\":\"" + pw + "\",\"fullName\":\"RL Test\"}");
            assertEquals(201, reg.status(), reg.body());
        }
        Resp regBlocked = HttpTestSupport.post(port, "/api/auth/register",
                "{\"email\":\"" + email() + "\",\"password\":\"" + pw + "\",\"fullName\":\"RL Test\"}");
        assertEquals(429, regBlocked.status());
        assertNotNull(regBlocked.header("Retry-After"));

        // CORS preflight and other endpoints are never limited
        for (int i = 0; i < 6; i++) {
            Resp preflight = HttpTestSupport.send(port, "OPTIONS", "/api/auth/login", Map.of(
                    "Origin", "http://localhost:5173",
                    "Access-Control-Request-Method", "POST",
                    "Access-Control-Request-Headers", "content-type"), null);
            assertNotEquals(429, preflight.status(), "preflight must not be rate limited");
            assertNotEquals(503, preflight.status());
        }
        assertEquals(200, HttpTestSupport.send(port, "GET", "/api/health", Map.of(), null).status());
    }
}
