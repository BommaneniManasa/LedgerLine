package com.snovasys.ledgerline.ratelimit;

import com.snovasys.ledgerline.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a request is refused by the rate limiter.
 * 429 = limit reached; 503 = the limiter itself (Redis) is unavailable and we fail CLOSED.
 * The message is generic on purpose: it never says which key, IP or account was counted.
 */
public class RateLimitException extends ApiException {

    private final long retryAfterSeconds;

    private RateLimitException(HttpStatus status, String code, String message, long retryAfterSeconds) {
        super(status, code, message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public static RateLimitException exceeded(long retryAfterSeconds) {
        return new RateLimitException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Too many requests. Please try again later.", retryAfterSeconds);
    }

    public static RateLimitException unavailable(long retryAfterSeconds) {
        return new RateLimitException(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE",
                "The service is temporarily unavailable. Please try again shortly.", retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
