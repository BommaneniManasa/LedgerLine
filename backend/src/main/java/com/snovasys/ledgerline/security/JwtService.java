
package com.snovasys.ledgerline.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.snovasys.ledgerline.config.AppProperties;
import com.snovasys.ledgerline.domain.User;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties.Jwt settings;
    private final Clock clock;

    @Autowired
    public JwtService(AppProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(AppProperties properties, Clock clock) {
        this.settings = properties.jwt();
        this.clock = clock;

        String secret = settings.secret();

        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "LEDGERLINE_JWT_SECRET must be set and at least "
                            + MIN_SECRET_BYTES + " bytes long");
        }

        SecretKey key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

        NimbusJwtDecoder nimbus = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        nimbus.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(settings.issuer())
        );

        this.decoder = nimbus;
    }

    public IssuedToken issue(User user) {

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(settings.expiry());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        String token = encoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        return new IssuedToken(token, expiresAt);
    }

    public Jwt decode(String token) {
        return decoder.decode(token);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
