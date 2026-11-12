package com.snovasys.ledgerline.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Talks to a REAL Redis. Skipped (never faked) unless LEDGERLINE_TEST_REDIS_URL is set, e.g.
 *   LEDGERLINE_TEST_REDIS_URL=redis://localhost:6379        (a throw-away local Redis is enough)
 * Every key uses a random prefix, so the test never touches other data.
 */
@EnabledIfEnvironmentVariable(named = "LEDGERLINE_TEST_REDIS_URL", matches = ".+")
class RedisRateLimitStoreIntegrationTest {

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;
    private static RedisRateLimitStore store;

    @BeforeAll
    static void connect() {
        factory = RateLimitRedisConfig.buildFactory(System.getenv("LEDGERLINE_TEST_REDIS_URL"));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        store = new RedisRateLimitStore(template);
    }

    @AfterAll
    static void disconnect() {
        if (factory != null) {
            factory.destroy();
        }
    }

    private static String key() {
        return "ll:rl:test:" + UUID.randomUUID();
    }

    @Test
    void countsUpAndRejectsAboveTheLimit() {
        String k = key();
        for (int i = 1; i <= 3; i++) {
            RateLimitDecision d = store.hit(k, 3, Duration.ofMinutes(1));
            assertTrue(d.allowed(), "request " + i);
            assertEquals(i, d.count());
        }
        RateLimitDecision over = store.hit(k, 3, Duration.ofMinutes(1));
        assertFalse(over.allowed());
        assertEquals(4, over.count());
        template.delete(k);
    }

    @Test
    void everyKeyGetsAnExpiryAndRetryAfterMatchesIt() {
        String k = key();
        RateLimitDecision d = store.hit(k, 1, Duration.ofSeconds(30));
        Long ttl = template.getExpire(k, TimeUnit.MILLISECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 30_000, "ttl=" + ttl);
        RateLimitDecision blocked = store.hit(k, 1, Duration.ofSeconds(30));
        assertFalse(blocked.allowed());
        assertTrue(blocked.retryAfterSeconds() >= 1 && blocked.retryAfterSeconds() <= 30, "" + blocked);
        Long ttlAfter = template.getExpire(k, TimeUnit.MILLISECONDS);
        assertTrue(ttlAfter <= ttl, "a blocked request must not extend the window");
        template.delete(k);
    }

    @Test
    void theWindowExpiresAndCountingStartsAgain() throws Exception {
        String k = key();
        assertTrue(store.hit(k, 1, Duration.ofSeconds(1)).allowed());
        assertFalse(store.hit(k, 1, Duration.ofSeconds(1)).allowed());
        Thread.sleep(1300);
        RateLimitDecision fresh = store.hit(k, 1, Duration.ofSeconds(1));
        assertTrue(fresh.allowed());
        assertEquals(1, fresh.count());
        template.delete(k);
    }

    @Test
    void concurrentRequestsCannotBypassTheLimit() throws Exception {
        String k = key();
        int threads = 32;
        int requests = 200;
        int limit = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RateLimitDecision>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return store.hit(k, limit, Duration.ofMinutes(1));
            }));
        }
        start.countDown();
        int allowed = 0;
        for (Future<RateLimitDecision> f : futures) {
            if (f.get(30, TimeUnit.SECONDS).allowed()) {
                allowed++;
            }
        }
        pool.shutdown();
        assertEquals(limit, allowed, "exactly the limit may pass, however many arrive at once");
        assertEquals(String.valueOf(requests), template.opsForValue().get(k));
        Long ttl = template.getExpire(k, TimeUnit.MILLISECONDS);
        assertTrue(ttl != null && ttl > 0, "the counter key must have an expiry");
        template.delete(k);
    }
}
