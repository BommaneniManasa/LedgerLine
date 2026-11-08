package com.snovasys.ledgerline.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** All application settings from application.yml / environment variables, in one typed place. */
@ConfigurationProperties(prefix = "ledgerline")
public record AppProperties(Jwt jwt, Cors cors, Money money, Security security, Bootstrap bootstrap) {

    public record Jwt(String secret, String issuer, Duration expiry) { }

    public record Cors(List<String> allowedOrigins) { }

    public record Money(List<String> supportedCurrencies, BigDecimal maxTransactionAmount, int maxAccountsPerUser) { }

    public record Security(int maxFailedLogins, Duration lockDuration) { }

    public record Bootstrap(String adminEmail, String adminPassword) { }
}
