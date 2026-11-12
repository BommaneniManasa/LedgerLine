package com.snovasys.ledgerline.ratelimit;

import java.time.Duration;

/** Where the counters live. Production: Redis. Tests: an in-memory fake. */
public interface RateLimitStore {

    /**
     * Counts one request for {@code key} in a fixed window of length {@code window} and says whether it is
     * still within {@code limit}. Must be atomic. If the store cannot be reached it MUST throw
     * {@link RateLimitException#unavailable(long)} (fail closed), never return "allowed".
     */
    RateLimitDecision hit(String key, int limit, Duration window);
}
