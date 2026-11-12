package com.snovasys.ledgerline.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.snovasys.ledgerline.TestProps;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RateLimitServiceTest {

    private static RateLimitProperties props(boolean enabled) {
        RateLimitProperties p = new RateLimitProperties();
        p.setEnabled(enabled);
        p.setLoginIp(new RateLimitProperties.Rule(3, Duration.ofMinutes(1)));
        p.setLoginIdentifier(new RateLimitProperties.Rule(3, Duration.ofMinutes(10)));
        p.setRegisterIp(new RateLimitProperties.Rule(2, Duration.ofHours(1)));
        return p;
    }

    private static RateLimitService service(RateLimitProperties p, RateLimitStore store) {
        return new RateLimitService(p, Optional.of(store), TestProps.create());
    }

    @Test
    void requestsBelowTheThresholdPass() {
        RateLimitService s = service(props(true), new FakeRateLimitStore());
        s.checkLoginIp("203.0.113.7");
        s.checkLoginIp("203.0.113.7");   // 2 of 3
    }

    @Test
    void requestExactlyAtTheThresholdPasses() {
        RateLimitService s = service(props(true), new FakeRateLimitStore());
        for (int i = 0; i < 3; i++) {
            s.checkLoginIp("203.0.113.7");   // the 3rd is exactly at the limit
        }
    }

    @Test
    void requestAboveTheThresholdIsRejectedWith429AndRetryAfter() {
        RateLimitService s = service(props(true), new FakeRateLimitStore());
        for (int i = 0; i < 3; i++) {
            s.checkLoginIp("203.0.113.7");
        }
        RateLimitException e = assertThrows(RateLimitException.class, () -> s.checkLoginIp("203.0.113.7"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
        assertEquals("RATE_LIMITED", e.getCode());
        assertTrue(e.getRetryAfterSeconds() >= 1 && e.getRetryAfterSeconds() <= 60,
                "Retry-After must be within the 60 s window but was " + e.getRetryAfterSeconds());
        assertFalse(e.getMessage().contains("203.0.113.7"), "message must not echo the client address");
    }

    @Test
    void retryAfterShrinksAsTheWindowRuns() {
        FakeRateLimitStore store = new FakeRateLimitStore();
        RateLimitService s = service(props(true), store);
        for (int i = 0; i < 3; i++) {
            s.checkLoginIp("203.0.113.7");
        }
        store.advance(Duration.ofSeconds(45));
        RateLimitException e = assertThrows(RateLimitException.class, () -> s.checkLoginIp("203.0.113.7"));
        assertEquals(15, e.getRetryAfterSeconds());
    }

    @Test
    void loginAndRegistrationHaveSeparateLimits() {
        RateLimitService s = service(props(true), new FakeRateLimitStore());
        for (int i = 0; i < 3; i++) {
            s.checkLoginIp("203.0.113.7");
        }
        assertThrows(RateLimitException.class, () -> s.checkLoginIp("203.0.113.7"));
        // same client, exhausted login budget - registration is counted separately
        s.checkRegisterIp("203.0.113.7");
        s.checkRegisterIp("203.0.113.7");
        assertThrows(RateLimitException.class, () -> s.checkRegisterIp("203.0.113.7"));
        // ... and the per-e-mail login counter is independent as well
        s.checkLoginIdentifier("alice@example.com");
    }

    @Test
    void differentClientsAndAccountsHaveTheirOwnCounters() {
        RateLimitService s = service(props(true), new FakeRateLimitStore());
        for (int i = 0; i < 3; i++) {
            s.checkLoginIp("203.0.113.7");
            s.checkLoginIdentifier("alice@example.com");
        }
        s.checkLoginIp("203.0.113.8");
        s.checkLoginIdentifier("bob@example.com");
        assertThrows(RateLimitException.class, () -> s.checkLoginIdentifier("alice@example.com"));
    }

    @Test
    void theWindowExpiresAndTheClientCanTryAgain() {
        FakeRateLimitStore store = new FakeRateLimitStore();
        RateLimitService s = service(props(true), store);
        for (int i = 0; i < 3; i++) {
            s.checkLoginIp("203.0.113.7");
        }
        assertThrows(RateLimitException.class, () -> s.checkLoginIp("203.0.113.7"));
        store.advance(Duration.ofSeconds(61));
        s.checkLoginIp("203.0.113.7");   // new window
    }

    @Test
    void redisKeysContainNoRawEmailOrIpAddress() {
        FakeRateLimitStore store = new FakeRateLimitStore();
        RateLimitService s = service(props(true), store);
        s.checkLoginIp("203.0.113.7");
        s.checkLoginIdentifier("alice@example.com");
        s.checkRegisterIp("203.0.113.7");
        assertEquals(3, store.keys().size());
        for (String key : store.keys()) {
            assertTrue(key.startsWith("ll:rl:v1:"), key);
            assertFalse(key.contains("203.0.113.7"), "no IP in key: " + key);
            assertFalse(key.toLowerCase().contains("alice"), "no e-mail in key: " + key);
            assertFalse(key.contains("example"), "no e-mail in key: " + key);
            assertTrue(key.matches("[a-z0-9:-]+"), "unexpected characters in key: " + key);
        }
    }

    @Test
    void theSameClientAlwaysMapsToTheSameKey() {
        FakeRateLimitStore store = new FakeRateLimitStore();
        RateLimitService s = service(props(true), store);
        s.checkLoginIdentifier("alice@example.com");
        s.checkLoginIdentifier("alice@example.com");
        assertEquals(1, store.keys().size());
    }

    @Test
    void whenRedisIsUnavailableTheRequestIsRejectedWith503NotAllowed() {
        FakeRateLimitStore store = new FakeRateLimitStore();
        store.down = true;
        RateLimitService s = service(props(true), store);
        for (Runnable check : List.<Runnable>of(() -> s.checkLoginIp("203.0.113.7"),
                () -> s.checkLoginIdentifier("alice@example.com"), () -> s.checkRegisterIp("203.0.113.7"))) {
            RateLimitException e = assertThrows(RateLimitException.class, check::run);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
            assertEquals("RATE_LIMIT_UNAVAILABLE", e.getCode());
            assertTrue(e.getRetryAfterSeconds() >= 1);
        }
    }

    @Test
    void anUnexpectedStoreErrorAlsoFailsClosed() {
        RateLimitStore broken = (key, limit, window) -> {
            throw new IllegalStateException("boom: secret-host.internal");
        };
        RateLimitService s = service(props(true), broken);
        RateLimitException e = assertThrows(RateLimitException.class, () -> s.checkLoginIp("203.0.113.7"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertFalse(e.getMessage().contains("secret-host"), "internal details must not reach the client");
    }

    @Test
    void whenDisabledNothingIsCountedAndRedisIsNeverTouched() {
        FakeRateLimitStore store = new FakeRateLimitStore();
        store.down = true;   // would fail closed if it were used
        RateLimitService s = service(props(false), store);
        for (int i = 0; i < 50; i++) {
            s.checkLoginIp("203.0.113.7");
            s.checkLoginIdentifier("alice@example.com");
            s.checkRegisterIp("203.0.113.7");
        }
        assertEquals(0, store.calls);
        assertFalse(s.isEnabled());
    }

    @Test
    void concurrentRequestsCannotExceedTheLimit() throws Exception {
        // This exercises the service logic with a synchronized in-memory store. The REAL atomicity guarantee
        // comes from the Redis Lua script and is covered by RedisRateLimitStoreIntegrationTest.
        RateLimitProperties p = props(true);
        p.setLoginIp(new RateLimitProperties.Rule(10, Duration.ofMinutes(1)));
        RateLimitService s = service(p, new FakeRateLimitStore());
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(pool.submit(() -> {
                try {
                    s.checkLoginIp("203.0.113.7");
                    allowed.incrementAndGet();
                } catch (RateLimitException e) {
                    rejected.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertEquals(10, allowed.get());
        assertEquals(90, rejected.get());
    }

    @Test
    void enablingWithoutAStoreIsAStartupError() {
        assertThrows(IllegalStateException.class,
                () -> new RateLimitService(props(true), Optional.empty(), TestProps.create()));
    }

    @Test
    void nonsenseLimitsAreRejectedAtStartup() {
        RateLimitProperties p = props(true);
        p.setLoginIp(new RateLimitProperties.Rule(0, Duration.ofMinutes(1)));
        assertThrows(IllegalStateException.class, () -> service(p, new FakeRateLimitStore()));
    }
}
