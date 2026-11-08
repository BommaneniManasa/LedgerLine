package com.snovasys.ledgerline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A JWT id (jti) that was logged out and must no longer be accepted. */
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    @Id
    private String jti;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant revokedAt = Instant.now();

    protected RevokedToken() {
        // required by JPA
    }

    public RevokedToken(String jti, UUID userId, Instant expiresAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String getJti() { return jti; }
    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
}
