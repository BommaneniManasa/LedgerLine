package com.snovasys.ledgerline.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under "ledgerline.rate-limit" (see application.yml).
 * Rate limiting is OFF by default (local development and tests). Production turns it on with
 * LEDGERLINE_RATE_LIMIT_ENABLED=true AND a Redis URL in LEDGERLINE_REDIS_URL.
 */
@ConfigurationProperties(prefix = "ledgerline.rate-limit")
public class RateLimitProperties {

    /** Master switch. When false, no Redis connection is created and nothing is limited. */
    private boolean enabled = false;
    /** redis:// or rediss:// URL. Contains a password in production: never log it. */
    private String redisUrl = "";
    /** Prefix of every Redis key. Change it to share one Redis with another app or to isolate tests. */
    private String keyPrefix = "ll:rl:v1";
    /**
     * How many trusted reverse proxies sit in front of the app and append to X-Forwarded-For.
     * 0 (default) = ignore the header completely and use the TCP peer address.
     */
    private int trustedProxyHops = 0;

    private Rule loginIp = new Rule(20, Duration.ofMinutes(5));
    private Rule loginIdentifier = new Rule(10, Duration.ofMinutes(15));
    private Rule registerIp = new Rule(5, Duration.ofHours(1));

    public static class Rule {
        private int limit;
        private Duration window;

        public Rule() {
        }

        public Rule(int limit, Duration window) {
            this.limit = limit;
            this.window = window;
        }

        public int getLimit() { return limit; }

        public void setLimit(int limit) { this.limit = limit; }

        public Duration getWindow() { return window; }

        public void setWindow(Duration window) { this.window = window; }

        void validate(String name) {
            if (limit < 1 || window == null || window.toMillis() < 1000) {
                throw new IllegalStateException("ledgerline.rate-limit." + name
                        + " needs limit >= 1 and window >= 1 second");
            }
        }
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getRedisUrl() { return redisUrl; }

    public void setRedisUrl(String redisUrl) { this.redisUrl = redisUrl; }

    public String getKeyPrefix() { return keyPrefix; }

    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public int getTrustedProxyHops() { return trustedProxyHops; }

    public void setTrustedProxyHops(int trustedProxyHops) { this.trustedProxyHops = trustedProxyHops; }

    public Rule getLoginIp() { return loginIp; }

    public void setLoginIp(Rule loginIp) { this.loginIp = loginIp; }

    public Rule getLoginIdentifier() { return loginIdentifier; }

    public void setLoginIdentifier(Rule loginIdentifier) { this.loginIdentifier = loginIdentifier; }

    public Rule getRegisterIp() { return registerIp; }

    public void setRegisterIp(Rule registerIp) { this.registerIp = registerIp; }
}
