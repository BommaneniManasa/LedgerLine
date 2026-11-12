package com.snovasys.ledgerline;

import com.snovasys.ledgerline.config.AppProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/** Builds settings for unit tests. The secret below is a TEST-ONLY fixture and protects nothing. */
public final class TestProps {

    public static final String TEST_ONLY_SECRET = "unit-test-only-secret-0123456789-abcdefghij";

    private TestProps() {
    }

    public static AppProperties create() {
        return create(TEST_ONLY_SECRET, "ledgerline", Duration.ofMinutes(30));
    }

    public static AppProperties create(String secret, String issuer, Duration expiry) {
        return new AppProperties(
                new AppProperties.Jwt(secret, issuer, expiry),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Money(List.of("INR", "USD", "EUR"), new BigDecimal("1000000.00"), 5),
                new AppProperties.Security(5, Duration.ofMinutes(15)),
                new AppProperties.Bootstrap("", ""));
    }
}
