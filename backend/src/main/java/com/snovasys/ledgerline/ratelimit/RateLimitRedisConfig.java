package com.snovasys.ledgerline.ratelimit;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Creates the Redis connection ONLY when ledgerline.rate-limit.enabled=true.
 * With the flag off nothing here is created, so local runs and tests need no Redis.
 * The connection is lazy: the app still starts if Redis is down, and requests then get 503 (fail closed).
 */
@Configuration
@ConditionalOnProperty(prefix = "ledgerline.rate-limit", name = "enabled", havingValue = "true")
public class RateLimitRedisConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Bean
    public LettuceConnectionFactory rateLimitRedisConnectionFactory(RateLimitProperties properties) {
        return buildFactory(properties.getRedisUrl());
    }

    @Bean
    public StringRedisTemplate rateLimitStringRedisTemplate(LettuceConnectionFactory rateLimitRedisConnectionFactory) {
        return new StringRedisTemplate(rateLimitRedisConnectionFactory);
    }

    @Bean
    public RateLimitStore rateLimitStore(StringRedisTemplate rateLimitStringRedisTemplate) {
        return new RedisRateLimitStore(rateLimitStringRedisTemplate);
    }

    /**
     * Builds a connection factory from a redis:// or rediss:// URL (rediss = TLS).
     * Fails fast (1 s) instead of queueing commands while Redis is unreachable.
     * Error messages never include the URL because it contains the password.
     */
    public static LettuceConnectionFactory buildFactory(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "LEDGERLINE_REDIS_URL must be set when LEDGERLINE_RATE_LIMIT_ENABLED=true");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("LEDGERLINE_REDIS_URL is not a valid redis:// or rediss:// URL");
        }
        String scheme = uri.getScheme();
        boolean tls = "rediss".equalsIgnoreCase(scheme);
        if (!tls && !"redis".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("LEDGERLINE_REDIS_URL must start with redis:// or rediss://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalStateException("LEDGERLINE_REDIS_URL has no host");
        }

        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(uri.getHost(),
                uri.getPort() > 0 ? uri.getPort() : 6379);
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            String user = colon >= 0 ? userInfo.substring(0, colon) : "";
            String password = colon >= 0 ? userInfo.substring(colon + 1) : userInfo;
            if (!user.isEmpty()) {
                server.setUsername(user);
            }
            server.setPassword(password);
        }
        String path = uri.getPath();
        if (path != null && path.length() > 1) {
            try {
                server.setDatabase(Integer.parseInt(path.substring(1)));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("LEDGERLINE_REDIS_URL database index must be a number");
            }
        }

        ClientOptions options = ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(TIMEOUT).build())
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true)
                .build();
        LettuceClientConfiguration.LettuceClientConfigurationBuilder client = LettuceClientConfiguration.builder()
                .clientOptions(options)
                .commandTimeout(TIMEOUT);
        LettuceClientConfiguration clientConfig = tls ? client.useSsl().and().build() : client.build();
        return new LettuceConnectionFactory(server, clientConfig);
    }
}
