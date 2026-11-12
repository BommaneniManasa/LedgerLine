package com.snovasys.ledgerline.ratelimit;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** In-memory stand-in for Redis with a controllable clock. Same fixed-window rules as fixed_window.lua. */
class FakeRateLimitStore implements RateLimitStore {

    final Map<String, long[]> counters = new HashMap<>();   // key -> {count, windowEndMillis}
    private long nowMillis = 0;
    boolean down = false;
    int calls = 0;

    @Override
    public synchronized RateLimitDecision hit(String key, int limit, Duration window) {
        calls++;
        if (down) {
            throw RateLimitException.unavailable(5);
        }
        long[] c = counters.get(key);
        if (c == null || nowMillis >= c[1]) {
            c = new long[] {0, nowMillis + window.toMillis()};
            counters.put(key, c);
        }
        c[0]++;
        long retryAfter = Math.max(1, (c[1] - nowMillis + 999) / 1000);
        return new RateLimitDecision(c[0] <= limit, c[0], retryAfter);
    }

    synchronized void advance(Duration d) {
        nowMillis += d.toMillis();
    }

    synchronized java.util.Set<String> keys() {
        return new java.util.HashSet<>(counters.keySet());
    }
}
