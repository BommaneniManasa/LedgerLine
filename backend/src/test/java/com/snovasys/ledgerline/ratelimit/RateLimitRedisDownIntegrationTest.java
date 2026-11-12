package com.snovasys.ledgerline.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.snovasys.ledgerline.ratelimit.HttpTestSupport.Resp;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Rate limiting ON but Redis unreachable (port 1): the app must still start, login/register must fail CLOSED
 * with 503 + Retry-After, and everything that is not rate limited must keep working.
 * Needs only the database variables (no Redis).
 */
@EnabledIfEnvironmentVariable(named = "LEDGERLINE_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LEDGERLINE_BOOTSTRAP_ADMIN_EMAIL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ledgerline.rate-limit.enabled=true",
        "ledgerline.rate-limit.redis-url=redis://127.0.0.1:1"
})
class RateLimitRedisDownIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void loginAndRegisterFailClosedWith503WhileRedisIsDown() throws Exception {
        Resp login = HttpTestSupport.post(port, "/api/auth/login", HttpTestSupport.loginJson("x@test.io"));
        assertEquals(503, login.status(), login.body());
        assertEquals("RATE_LIMIT_UNAVAILABLE", login.field("code"));
        assertNotNull(login.header("Retry-After"));

        Resp register = HttpTestSupport.post(port, "/api/auth/register",
                "{\"email\":\"x@test.io\",\"password\":\"Str0ngPassw0rd!\",\"fullName\":\"X\"}");
        assertEquals(503, register.status(), register.body());
        assertNotNull(register.header("Retry-After"));
    }

    @Test
    void everythingElseKeepsWorking() throws Exception {
        assertEquals(200, HttpTestSupport.send(port, "GET", "/api/health", Map.of(), null).status());
        assertEquals(401, HttpTestSupport.send(port, "GET", "/api/accounts", Map.of(), null).status());
        Resp preflight = HttpTestSupport.send(port, "OPTIONS", "/api/auth/login", Map.of(
                "Origin", "http://localhost:5173", "Access-Control-Request-Method", "POST"), null);
        assertNotEquals(503, preflight.status());
    }
}
