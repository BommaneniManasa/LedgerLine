package com.snovasys.ledgerline.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

/** Runs without any Redis server: port 1 refuses connections, which is exactly "Redis is down". */
class RedisRateLimitStoreUnavailableTest {

    @Test
    void anUnreachableRedisFailsClosedWith503Quickly() {
        LettuceConnectionFactory factory = RateLimitRedisConfig.buildFactory("redis://127.0.0.1:1");
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
            RedisRateLimitStore store = new RedisRateLimitStore(template);
            long started = System.nanoTime();
            RateLimitException e = assertThrows(RateLimitException.class,
                    () -> store.hit("ll:rl:test:down", 5, Duration.ofMinutes(1)));
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toSeconds() < 15, "must fail fast");
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
            assertEquals("RATE_LIMIT_UNAVAILABLE", e.getCode());
            assertTrue(e.getRetryAfterSeconds() >= 1);
        } finally {
            factory.destroy();
        }
    }

    @Test
    void aMissingOrInvalidRedisUrlIsAStartupError() {
        assertThrows(IllegalStateException.class, () -> RateLimitRedisConfig.buildFactory(""));
        assertThrows(IllegalStateException.class, () -> RateLimitRedisConfig.buildFactory(null));
        assertThrows(IllegalStateException.class, () -> RateLimitRedisConfig.buildFactory("http://example.com"));
        assertThrows(IllegalStateException.class, () -> RateLimitRedisConfig.buildFactory("redis://:pw@/0"));
    }

    @Test
    void theUrlErrorMessageNeverContainsThePassword() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> RateLimitRedisConfig.buildFactory("ftp://user:Sup3rSecret@host:6379"));
        assertTrue(!e.getMessage().contains("Sup3rSecret"));
    }
}
