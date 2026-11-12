package com.snovasys.ledgerline.ratelimit;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Fixed-window counter in Redis. The INCR + PEXPIRE pair runs as one Lua script
 * (resources/ratelimit/fixed_window.lua), which Redis executes atomically.
 * Any Redis problem (down, timeout, bad reply) becomes a 503 - the request is NOT let through.
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);
    private static final long UNAVAILABLE_RETRY_SECONDS = 5;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptSource(new ResourceScriptSource(new ClassPathResource("ratelimit/fixed_window.lua")));
        this.script.setResultType(List.class);
    }

    @Override
    public RateLimitDecision hit(String key, int limit, Duration window) {
        List<?> reply;
        try {
            reply = redis.execute(script, List.of(key), Long.toString(window.toMillis()));
        } catch (RuntimeException e) {
            // Log the exception TYPE only: connection errors can contain the Redis host name.
            log.warn("Rate limiter: Redis call failed ({}). Failing closed with 503.", e.getClass().getSimpleName());
            throw RateLimitException.unavailable(UNAVAILABLE_RETRY_SECONDS);
        }
        if (reply == null || reply.size() < 2 || !(reply.get(0) instanceof Number) || !(reply.get(1) instanceof Number)) {
            log.warn("Rate limiter: unexpected Redis reply. Failing closed with 503.");
            throw RateLimitException.unavailable(UNAVAILABLE_RETRY_SECONDS);
        }
        long count = ((Number) reply.get(0)).longValue();
        long ttlMillis = ((Number) reply.get(1)).longValue();
        long retryAfter = Math.max(1, (ttlMillis + 999) / 1000);   // round up to whole seconds
        return new RateLimitDecision(count <= limit, count, retryAfter);
    }
}
