package com.snovasys.ledgerline.ratelimit;

/** Result of counting one request. retryAfterSeconds = time until the current window ends (always >= 1). */
public record RateLimitDecision(boolean allowed, long count, long retryAfterSeconds) {
}
