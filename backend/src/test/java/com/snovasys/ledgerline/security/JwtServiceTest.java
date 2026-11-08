
package com.snovasys.ledgerline.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.snovasys.ledgerline.TestProps;
import com.snovasys.ledgerline.domain.Role;
import com.snovasys.ledgerline.domain.User;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private JwtService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new JwtService(TestProps.create());
        user = new User("a@example.com", "hash", "A", null, Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    @Test
    void issuedTokenIsValidAndCarriesOnlyIdentityClaims() {
        JwtService.IssuedToken token = service.issue(user);
        Jwt jwt = service.decode(token.value());

        assertEquals(user.getId().toString(), jwt.getSubject());
        assertEquals("https://ledgerline.snovasys.local", jwt.getIssuer().toString());
        assertNotNull(jwt.getId());
        assertNotNull(jwt.getExpiresAt());

        // Role must come from the database, never from the token.
        assertEquals(false, jwt.getClaims().containsKey("role"));
        assertEquals(false, jwt.getClaims().containsKey("roles"));
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = service.issue(user).value();

        String tampered = token.substring(0, token.length() - 4)
                + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertThrows(JwtException.class, () -> service.decode(tampered));
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = service.issue(user).value().split("\\.");

        String forgedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        ("{\"sub\":\"" + UUID.randomUUID()
                                + "\",\"iss\":\"https://ledgerline.snovasys.local\",\"exp\":9999999999}")
                                .getBytes(StandardCharsets.UTF_8)
                );

        assertThrows(
                JwtException.class,
                () -> service.decode(parts[0] + "." + forgedPayload + "." + parts[2])
        );
    }

    @Test
    void expiredTokenIsRejected() {

        Clock expiredClock = Clock.fixed(
                Instant.now().minus(Duration.ofHours(1)),
                ZoneOffset.UTC
        );

        JwtService shortLived = new JwtService(
                TestProps.create(
                        TestProps.TEST_ONLY_SECRET,
                        "https://ledgerline.snovasys.local",
                        Duration.ofMinutes(5)
                ),
                expiredClock
        );

        String token = shortLived.issue(user).value();

        assertThrows(JwtException.class, () -> service.decode(token));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService(
                TestProps.create(
                        "a-completely-different-secret-0123456789xyz",
                        "https://ledgerline.snovasys.local",
                        Duration.ofMinutes(5)
                )
        );

        assertThrows(
                JwtException.class,
                () -> service.decode(other.issue(user).value())
        );
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtService other = new JwtService(
                TestProps.create(
                        TestProps.TEST_ONLY_SECRET,
                        "https://someone-else.example",
                        Duration.ofMinutes(5)
                )
        );

        assertThrows(
                JwtException.class,
                () -> service.decode(other.issue(user).value())
        );
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();

        String header = enc.encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}"
                        .getBytes(StandardCharsets.UTF_8)
        );

        String payload = enc.encodeToString(
                ("{\"sub\":\"" + user.getId()
                        + "\",\"iss\":\"https://ledgerline.snovasys.local\",\"exp\":9999999999}")
                        .getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(
                JwtException.class,
                () -> service.decode(header + "." + payload + ".")
        );
    }

    @Test
    void garbageIsRejected() {
        assertThrows(JwtException.class, () -> service.decode("not-a-jwt"));
        assertThrows(JwtException.class, () -> service.decode(""));
    }

    @Test
    void weakOrMissingSecretStopsStartup() {
        assertThrows(
                IllegalStateException.class,
                () -> new JwtService(
                        TestProps.create(
                                "too-short",
                                "https://ledgerline.snovasys.local",
                                Duration.ofMinutes(5)
                        )
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> new JwtService(
                        TestProps.create(
                                null,
                                "https://ledgerline.snovasys.local",
                                Duration.ofMinutes(5)
                        )
                )
        );
    }
}
